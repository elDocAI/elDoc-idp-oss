/*
 *
 *  * Copyright (C) 2022-2026 elDoc Limited
 *  *
 *  * This file is part of elDoc IDP OSS.
 *  *
 *  * elDoc IDP OSS is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU Affero General Public License as published
 *  * by the Free Software Foundation, version 3.
 *  *
 *  * elDoc IDP OSS is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 *  * General Public License for more details.
 *  *
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */
package ai.eldoc.service;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.service.AppConfigService.EmbeddedMongoConfig;
import ai.eldoc.service.AppConfigService.TenantConfig;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoSocketException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.flapdoodle.commons.reverse.TransitionWalker;
import de.flapdoodle.commons.reverse.transitions.Start;
import de.flapdoodle.embed.mongo.commands.MongodArguments;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.Storage;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.distribution.Versions;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.mongo.types.DatabaseDir;
import de.flapdoodle.embed.process.io.directories.PersistentDir;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.*;
import jakarta.inject.Inject;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;


/**
 * Starts a local MongoDB fallback for tenants without an external connection.
 */
@Startup
@Singleton
@DependsOn("AppConfigService")
@TransactionManagement(TransactionManagementType.BEAN)
public class EmbeddedMongoService {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedMongoService.class);
    private static final Duration REPLICA_SET_START_TIMEOUT = Duration.ofSeconds(30);

    @Inject
    private AppConfigService _appConfigService;

    private TransitionWalker.ReachedState<RunningMongodProcess> _mongodState;
    private String _connectionString;
    private Path _endpointMetadataFile;
    private Endpoint _activeEndpoint;
    private Endpoint _managedEndpoint;

    record Endpoint(String host, int port) {

    }


    @PostConstruct
    void init() {
        List<TenantConfig> fallbackTenants = _appConfigService.getTenantConfigsRequiringEmbeddedMongo();
        if (fallbackTenants.isEmpty()) {
            LOG.info("EmbeddedMongoService: All tenants have external MongoDB connections; embedded MongoDB will not start");
            return;
        }

        EmbeddedMongoConfig config = _appConfigService.getEmbeddedMongoConfig();
        try {
            start(config);
            for (TenantConfig tenantConfig : fallbackTenants) {
                if (!_appConfigService.applyEmbeddedMongoConnection(tenantConfig.getTenantId(), _connectionString)) {
                    throw new ElDocRuntimeException("Tenant[" + tenantConfig.getTenantId() + "] MongoDB connection changed while embedded MongoDB was starting");
                }
            }
            LOG.info("EmbeddedMongoService: Embedded MongoDB replica set '{}' is ready at {} for {} tenant(s); data directory: {}; binary cache: {}",
                    config.getReplicaSet(), endpoint(_activeEndpoint), fallbackTenants.size(), config.getDataDir(), config.getBinaryCacheDir());
        } catch (Exception e) {
            closeAfterFailedStart();
            throw new ElDocRuntimeException("Embedded MongoDB initialization failed for data directory '" + config.getDataDir()
                    + "', binary cache '" + config.getBinaryCacheDir() + "', replica set '" + config.getReplicaSet() + "': " + e.getMessage(), e);
        }
    }


    void start(EmbeddedMongoConfig config) throws IOException, InterruptedException {
        prepareDirectory(config.getDataDir(), "data");
        prepareDirectory(config.getBinaryCacheDir(), "binary cache");
        validateLoopbackHost(config.getHost());
        _endpointMetadataFile = endpointMetadataFile(config.getDataDir());
        if (reuseExistingProcess(config)) {
            return;
        }
        boolean persistedDatabase = directoryHasEntries(config.getDataDir());

        int port = config.getPort() == 0 ? findFreePort(config.getHost()) : config.getPort();
        MongodArguments arguments = MongodArguments.builder()
                .replication(Storage.of(config.getReplicaSet(), 0))
                .useNoJournal(false)
                .build();

        Mongod mongod = Mongod.builder()
                .from(EmbeddedMongoFactory.create())
                .net(Start.to(Net.class).initializedWith(Net.of(config.getHost(), port, false)))
                .databaseDir(Start.to(DatabaseDir.class).initializedWith(DatabaseDir.of(config.getDataDir())))
                .persistentBaseDir(Start.to(PersistentDir.class).initializedWith(PersistentDir.of(config.getBinaryCacheDir())))
                .mongodArguments(Start.to(MongodArguments.class).initializedWith(arguments))
                .build();

        de.flapdoodle.embed.process.distribution.Version version = resolveVersion(config.getVersion());
        LOG.info("EmbeddedMongoService: Starting MongoDB {} on {}:{} as replica set '{}'; first launch may download its binary",
                version.asInDownloadPath(), config.getHost(), port, config.getReplicaSet());
        _mongodState = mongod.start(version);

        RunningMongodProcess process = _mongodState.current();
        String host = process.getServerAddress().getHost();
        int runtimePort = process.getServerAddress().getPort();
        _activeEndpoint = new Endpoint(host, runtimePort);
        _managedEndpoint = _activeEndpoint;
        writeEndpointMetadata(_endpointMetadataFile, _activeEndpoint);
        initializeReplicaSet(host, runtimePort, config.getReplicaSet(), persistedDatabase);
        _connectionString = replicaSetConnectionString(host, runtimePort, config.getReplicaSet());
    }


    private boolean reuseExistingProcess(EmbeddedMongoConfig config) throws IOException, InterruptedException {
        List<Endpoint> candidates = new ArrayList<>();
        if (Files.isRegularFile(_endpointMetadataFile)) {
            Properties metadata = new Properties();
            try (var input = Files.newInputStream(_endpointMetadataFile)) {
                metadata.load(input);
                int port = Integer.parseInt(metadata.getProperty("port", ""));
                String host = metadata.getProperty("host", "");
                if (port > 0 && port <= 65535 && !host.isBlank()) {
                    candidates.add(new Endpoint(host, port));
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("EmbeddedMongoService: Ignoring invalid MongoDB endpoint metadata at {}", _endpointMetadataFile);
            }
        }
        if (config.getPort() != 0) {
            Endpoint configuredEndpoint = new Endpoint(config.getHost(), config.getPort());
            if (!candidates.contains(configuredEndpoint)) {
                candidates.add(configuredEndpoint);
            }
        }
        Endpoint processEndpoint = findRunningMongodEndpoint(config);
        if (processEndpoint != null && !candidates.contains(processEndpoint)) {
            candidates.add(processEndpoint);
        }

        for (Endpoint candidate : candidates) {
            try {
                validateLoopbackHost(candidate.host());
                if (isExpectedProcess(candidate, config)) {
                    initializeReplicaSet(candidate.host(), candidate.port(), config.getReplicaSet(), true);
                    _activeEndpoint = candidate;
                    _connectionString = replicaSetConnectionString(candidate.host(), candidate.port(), config.getReplicaSet());
                    LOG.info("EmbeddedMongoService: Reusing existing MongoDB replica set '{}' at {} with data directory {}",
                            config.getReplicaSet(), endpoint(candidate.host(), candidate.port()), config.getDataDir());
                    return true;
                }
            } catch (IOException | IllegalArgumentException e) {
                LOG.warn("EmbeddedMongoService: Ignoring invalid MongoDB endpoint {} from metadata", endpoint(candidate));
            }
        }
        return false;
    }


    private static Endpoint findRunningMongodEndpoint(EmbeddedMongoConfig config) {
        try (Stream<ProcessHandle> processes = ProcessHandle.allProcesses()) {
            return processes.map(process -> endpointFromProcess(process, config))
                    .filter(endpoint -> endpoint != null)
                    .findFirst()
                    .orElseGet(() -> findWindowsMongodEndpoint(config));
        } catch (SecurityException e) {
            LOG.debug("EmbeddedMongoService: Cannot inspect local process arguments to find an existing MongoDB process", e);
            return null;
        }
    }


    private static Endpoint findWindowsMongodEndpoint(EmbeddedMongoConfig config) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            return null;
        }

        try {
            Process netstat = new ProcessBuilder("netstat", "-ano", "-p", "TCP").redirectErrorStream(true).start();
            List<String> lines;
            try (var reader = netstat.inputReader()) {
                lines = reader.lines().toList();
            }
            if (!netstat.waitFor(5, TimeUnit.SECONDS) || netstat.exitValue() != 0) {
                return null;
            }
            for (String line : lines) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length != 5 || !"TCP".equalsIgnoreCase(fields[0]) || !"LISTENING".equalsIgnoreCase(fields[3])) {
                    continue;
                }
                try {
                    long pid = Long.parseLong(fields[4]);
                    ProcessHandle process = ProcessHandle.of(pid).orElse(null);
                    if (process == null || !isCachedMongodProcess(process, config)) {
                        continue;
                    }
                    int port = Integer.parseInt(fields[1].substring(fields[1].lastIndexOf(':') + 1));
                    Endpoint endpoint = new Endpoint(config.getHost(), port);
                    if (isExpectedProcess(endpoint, config)) {
                        return endpoint;
                    }
                } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                    // Not a usable TCP endpoint.
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.debug("EmbeddedMongoService: Cannot inspect Windows TCP listeners for an existing MongoDB process", e);
        }
        return null;
    }


    private static boolean isCachedMongodProcess(ProcessHandle process, EmbeddedMongoConfig config) {
        String command = process.info().command().orElse("");
        try {
            Path executable = Path.of(command).toRealPath();
            Path name = executable.getFileName();
            return name != null && ("mongod".equalsIgnoreCase(name.toString()) || "mongod.exe".equalsIgnoreCase(name.toString()))
                    && executable.startsWith(config.getBinaryCacheDir().toRealPath());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }


    private static Endpoint endpointFromProcess(ProcessHandle process, EmbeddedMongoConfig config) {
        ProcessHandle.Info info = process.info();
        if (!isCachedMongodProcess(process, config)) {
            return null;
        }

        String[] arguments = info.arguments().orElse(new String[0]);
        String dataDir = processArgument(arguments, "--dbpath");
        String replicaSet = processArgument(arguments, "--replSet");
        String portValue = processArgument(arguments, "--port");
        if (dataDir == null || !config.getReplicaSet().equals(replicaSet) || portValue == null) {
            return null;
        }

        try {
            if (!Files.isSameFile(config.getDataDir(), Path.of(dataDir))) {
                return null;
            }
            int port = Integer.parseInt(portValue);
            return port > 0 && port <= 65535 ? new Endpoint(config.getHost(), port) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }


    private static String processArgument(String[] arguments, String option) {
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i].startsWith(option + "=")) {
                return arguments[i].substring(option.length() + 1);
            }
            if (option.equals(arguments[i]) && i + 1 < arguments.length) {
                return arguments[i + 1];
            }
        }
        return null;
    }


    static boolean isExpectedProcess(Endpoint endpoint, EmbeddedMongoConfig config) {
        String connectionString = directConnectionString(endpoint.host(), endpoint.port());
        try (MongoClient client = MongoClients.create(connectionString)) {
            Document hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
            if (hello.getString("setName") != null && !config.getReplicaSet().equals(hello.getString("setName"))) {
                return false;
            }

            Number pid = client.getDatabase("admin").runCommand(new Document("serverStatus", 1)).get("pid", Number.class);
            if (pid == null || ProcessHandle.of(pid.longValue())
                    .filter(process -> isCachedMongodProcess(process, config)).isEmpty()) {
                return false;
            }

            Document options = client.getDatabase("admin").runCommand(new Document("getCmdLineOpts", 1));
            Document parsed = options.get("parsed", Document.class);
            Document storage = parsed == null ? null : parsed.get("storage", Document.class);
            Document replication = parsed == null ? null : parsed.get("replication", Document.class);
            String dbPath = storage == null ? null : storage.getString("dbPath");
            String replicaSet = replication == null ? null : replication.getString("replSet");
            return config.getReplicaSet().equals(replicaSet)
                    && dbPath != null
                    && Files.isSameFile(config.getDataDir(), Path.of(dbPath));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }


    private static Path endpointMetadataFile(Path dataDir) {
        Path name = dataDir.getFileName();
        String filePrefix = name == null ? "eldoc" : name.toString();
        return dataDir.resolveSibling(filePrefix + ".eldoc-mongod.properties");
    }


    private static void writeEndpointMetadata(Path metadataFile, Endpoint endpoint) throws IOException {
        Properties metadata = new Properties();
        metadata.setProperty("host", endpoint.host());
        metadata.setProperty("port", Integer.toString(endpoint.port()));
        Path temporaryFile = Files.createTempFile(metadataFile.getParent(), metadataFile.getFileName().toString(), ".tmp");
        try {
            try (var output = Files.newOutputStream(temporaryFile)) {
                metadata.store(output, "Embedded MongoDB endpoint");
            }
            try {
                Files.move(temporaryFile, metadataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, metadataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }


    static void prepareDirectory(Path directory, String purpose) throws IOException {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IOException("Cannot create embedded MongoDB " + purpose + " directory: " + directory, e);
        }
        if (!Files.isDirectory(directory) || !Files.isWritable(directory)) {
            throw new IOException("Embedded MongoDB " + purpose + " path is not a writable directory: " + directory);
        }
    }


    private static boolean directoryHasEntries(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isPresent();
        }
    }


    static de.flapdoodle.embed.process.distribution.Version resolveVersion(String configuredVersion) {
        String version = configuredVersion.trim();
        // Flapdoodle 5.0.0 maps the 8.2 line to 8.2.9; pin the newer patch on every OS.
        if ("8.2".equals(version) || "8.2.12".equals(version)) {
            return Versions.withFeatures(de.flapdoodle.embed.process.distribution.Version.of("8.2.12"));
        }
        de.flapdoodle.embed.process.distribution.Version mainVersion = switch (version) {
            case "7", "7.0" -> Version.Main.V7_0;
            case "8", "8.0" -> Version.Main.V8_0;
            case "8.1" -> Version.Main.V8_1;
            case "8.3" -> Version.Main.V8_3;
            default -> null;
        };
        if (mainVersion != null) {
            return mainVersion;
        }

        return Arrays.stream(Version.values())
                .filter(candidate -> candidate.asInDownloadPath().equals(version))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported embedded MongoDB version: " + configuredVersion));
    }


    static String replicaSetConnectionString(String host, int port, String replicaSet) {
        String uriHost = host.contains(":") ? "[" + host + "]" : host;
        return "mongodb://" + uriHost + ":" + port + "/?replicaSet=" + replicaSet;
    }


    private static String directConnectionString(String host, int port) {
        String uriHost = host.contains(":") ? "[" + host + "]" : host;
        return "mongodb://" + uriHost + ":" + port + "/?directConnection=true&serverSelectionTimeoutMS=1000";
    }


    private static void validateLoopbackHost(String host) throws IOException {
        InetAddress address = InetAddress.getByName(host);
        if (!address.isLoopbackAddress()) {
            throw new IllegalArgumentException("Embedded MongoDB host must resolve to a loopback address: " + host);
        }
    }


    private static int findFreePort(String host) throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName(host))) {
            return socket.getLocalPort();
        }
    }


    private static void initializeReplicaSet(String host, int port, String replicaSet, boolean persistedDatabase) throws InterruptedException {
        String directConnection = directConnectionString(host, port);
        try (MongoClient client = MongoClients.create(directConnection)) {
            Document member = new Document("_id", 0).append("host", endpoint(host, port));
            Document config = new Document("_id", replicaSet).append("members", List.of(member));
            Document persistedConfig = readReplicaSetConfig(client, persistedDatabase);
            if (persistedConfig == null) {
                try {
                    client.getDatabase("admin").runCommand(new Document("replSetInitiate", config));
                } catch (MongoSocketException e) {
                    LOG.debug("EmbeddedMongoService: Connection changed while initiating replica set '{}': {}", replicaSet, e.getMessage());
                }
            } else {
                LOG.info("EmbeddedMongoService: Replica set '{}' was already initialized; reusing persisted state", replicaSet);
                try {
                    reconfigureReplicaSetEndpointIfNeeded(client, persistedConfig, host, port, replicaSet);
                } catch (MongoSocketException e) {
                    LOG.debug("EmbeddedMongoService: Connection changed while reconfiguring replica set '{}': {}", replicaSet, e.getMessage());
                }
            }

            long deadline = System.nanoTime() + REPLICA_SET_START_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                try {
                    Document hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
                    if (Boolean.TRUE.equals(hello.getBoolean("isWritablePrimary"))) {
                        return;
                    }
                } catch (RuntimeException e) {
                    LOG.debug("EmbeddedMongoService: Waiting for replica set '{}' primary election: {}", replicaSet, e.getMessage());
                }
                Thread.sleep(250L);
            }
        }
        throw new ElDocRuntimeException("Timed out waiting for embedded MongoDB replica set '" + replicaSet + "' to elect a primary");
    }


    private static Document readReplicaSetConfig(MongoClient client, boolean waitForPersistedConfig) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        do {
            try {
                Document result = client.getDatabase("admin").runCommand(new Document("replSetGetConfig", 1));
                return result.get("config", Document.class);
            } catch (MongoCommandException e) {
                if (e.getErrorCode() != 94) {
                    throw e;
                }
            } catch (MongoSocketException e) {
                if (!waitForPersistedConfig) {
                    throw e;
                }
            }
            if (!waitForPersistedConfig) {
                return null;
            }
            Thread.sleep(200L);
        } while (System.nanoTime() < deadline);
        return null;
    }


    private static void reconfigureReplicaSetEndpointIfNeeded(MongoClient client, Document persistedConfig, String host, int port, String replicaSet) {
        if (persistedConfig == null) {
            throw new ElDocRuntimeException("MongoDB did not return persisted configuration for replica set '" + replicaSet + "'");
        }

        List<Document> members = persistedConfig.getList("members", Document.class);
        String runtimeEndpoint = endpoint(host, port);
        if (members != null && members.size() == 1 && runtimeEndpoint.equals(members.getFirst().getString("host"))) {
            return;
        }

        Number currentVersion = persistedConfig.get("version", Number.class);
        persistedConfig.put("version", currentVersion == null ? 2 : currentVersion.intValue() + 1);
        persistedConfig.put("members", List.of(new Document("_id", 0).append("host", runtimeEndpoint)));
        client.getDatabase("admin").runCommand(new Document("replSetReconfig", persistedConfig).append("force", true));
        LOG.info("EmbeddedMongoService: Replica set '{}' endpoint updated to {} for this launch", replicaSet, runtimeEndpoint);
    }


    private static String endpoint(RunningMongodProcess process) {
        return endpoint(process.getServerAddress().getHost(), process.getServerAddress().getPort());
    }


    private static String endpoint(Endpoint endpoint) {
        return endpoint(endpoint.host(), endpoint.port());
    }


    private static String endpoint(String host, int port) {
        String endpointHost = host.contains(":") ? "[" + host + "]" : host;
        return endpointHost.toLowerCase(Locale.ROOT) + ":" + port;
    }


    private void closeAfterFailedStart() {
        if (_mongodState != null) {
            boolean stopped = false;
            try {
                _mongodState.close();
                stopped = true;
            } catch (Exception closeError) {
                LOG.warn("EmbeddedMongoService: Error stopping MongoDB after failed initialization", closeError);
            } finally {
                if (stopped) {
                    deleteEndpointMetadata();
                }
                _mongodState = null;
                _activeEndpoint = null;
                _managedEndpoint = null;
                _connectionString = null;
            }
        }
    }


    @PreDestroy
    public void destroy() {
        if (_mongodState == null) {
            _connectionString = null;
            _activeEndpoint = null;
            return;
        }
        boolean stopped = false;
        try {
            LOG.info("EmbeddedMongoService: Stopping embedded MongoDB; persisted data will remain on disk");
            _mongodState.close();
            stopped = true;
            LOG.info("EmbeddedMongoService: Embedded MongoDB stopped");
        } catch (Exception e) {
            LOG.error("EmbeddedMongoService: Error stopping embedded MongoDB", e);
        } finally {
            if (stopped) {
                deleteEndpointMetadata();
            }
            _mongodState = null;
            _connectionString = null;
            _activeEndpoint = null;
            _managedEndpoint = null;
        }
    }


    private void deleteEndpointMetadata() {
        if (_endpointMetadataFile == null || _managedEndpoint == null || !Files.exists(_endpointMetadataFile)) {
            return;
        }
        Properties metadata = new Properties();
        try (var input = Files.newInputStream(_endpointMetadataFile)) {
            metadata.load(input);
            Endpoint recordedEndpoint = new Endpoint(metadata.getProperty("host", ""),
                    Integer.parseInt(metadata.getProperty("port", "")));
            if (_managedEndpoint.equals(recordedEndpoint)) {
                Files.deleteIfExists(_endpointMetadataFile);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("EmbeddedMongoService: Could not remove MongoDB endpoint metadata at {}", _endpointMetadataFile, e);
        }
    }


    String getConnectionString() {
        return _connectionString;
    }
}
