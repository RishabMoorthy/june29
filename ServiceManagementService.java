package com.stubserver.backend.service;

import com.stubserver.backend.config.AppProperties;
import org.common.db.entity.AssignedService;
import org.common.db.entity.VSDetails;
import com.stubserver.backend.exception.BadRequestException;
import com.stubserver.backend.exception.NotFoundException;
import org.common.db.repository.AssignedServiceRepository;
import org.common.db.repository.VsRepository;
import com.stubserver.backend.util.FilePathGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceManagementService {

    private final AssignedServiceRepository assignedServiceRepo;
    private final VsRepository vsDetailsRepo;
    private final AppProperties appProperties;

    public Map<String, String> assignServices(String username, List<String> services) {
        assignedServiceRepo.deleteByIdUsername(username);
        List<AssignedService> entries = services.stream()
                .map(s -> new AssignedService(username, s))
                .toList();
        assignedServiceRepo.saveAll(entries);
        return Map.of("message", "Services assigned successfully");
    }

    public Map<String, Object> getAssignedServices(String username) {
        List<String> services = assignedServiceRepo.findByIdUsername(username)
                .stream().map(a -> a.getId().getServiceName()).toList();
        return Map.of("username", username, "assignedService", services);
    }

    public Map<String, Object> getGroupTagsConfig(String serviceName) {
        VSDetails vs = vsDetailsRepo.findByVsname(serviceName).orElse(null);
        String group = vs != null && vs.getGroup() != null ? vs.getGroup().trim() : "";
        String tagsRaw = vs != null && vs.getTags() != null ? vs.getTags() : "";
        List<String> tags = tagsRaw.isEmpty() ? List.of() : Arrays.asList(tagsRaw.split(","));
        return Map.of("group", group, "tags", tags);
    }

    public Map<String, Boolean> updateGroup(String serviceName, String group) {
        VSDetails vs = vsDetailsRepo.findByVsname(serviceName)
                .orElseThrow(() -> new NotFoundException("Service not found: " + serviceName));
        vs.setGroup(group != null && !group.trim().isEmpty() ? group : null);
        vsDetailsRepo.save(vs);
        return Map.of("success", true);
    }

    public Map<String, Boolean> updateTags(String serviceName, List<String> tags) {
        if (tags == null) {
            throw new BadRequestException("Tags must be an array.");
        }
        List<String> trimmed = tags.stream().map(String::trim).toList();
        if (new HashSet<>(trimmed).size() != trimmed.size()) {
            throw new BadRequestException("Duplicate tags are not allowed.");
        }
        VSDetails vs = vsDetailsRepo.findByVsname(serviceName)
                .orElseThrow(() -> new NotFoundException("Service not found: " + serviceName));
        vs.setTags(trimmed.isEmpty() ? null : String.join(",", trimmed));
        vsDetailsRepo.save(vs);
        return Map.of("success", true);
    }

    public Map<String, Object> getDatasourceLists() {
        List<Map<String, Object>> data = vsDetailsRepo.findAll().stream()
                .filter(v -> {
                    String val = v.getDatasourceEnabled();
                    if (val == null) return false;
                    String upper = val.trim().toUpperCase();
                    return upper.equals("1") || upper.equals("Y") || upper.equals("TRUE");
                })
                .map(v -> Map.<String, Object>of("serviceName", v.getVsName(), "datasourceEnabled", true))
                .toList();
        return Map.of("data", data);
    }

    // ===== Dataset file operations =====

    private List<Path> datasetRoots() {
        String raw = appProperties.getDatasetPaths();
        if (raw == null || raw.isEmpty()) return List.of();
        List<Path> roots = Arrays.stream(raw.split("[;,]|" + java.io.File.pathSeparator))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Path::of).toList();
        roots.forEach(root -> log.info("[datasetRoots] dataset path configured='{}', resolved absolute path='{}'", root, root.toAbsolutePath().normalize()));
        return roots;
    }

    public Map<String, Object> getDatasets(String serviceName) {
        if (!FilePathGuard.isSafeFileName(serviceName)) {
            throw new BadRequestException("Invalid serviceName.");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Path root : datasetRoots()) {
            Path svcDir = root.resolve(serviceName);
            if (!Files.isDirectory(svcDir)) continue;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(svcDir)) {
                for (Path file : ds) {
                    if (!Files.isRegularFile(file)) continue;
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                        results.add(Map.of(
                                "fileName", file.getFileName().toString(),
                                "size", attrs.size(),
                                "lastModified", attrs.lastModifiedTime().toInstant()
                        ));
                    } catch (IOException e) {
                        log.warn("Stat error: {}", file);
                    }
                }
            } catch (IOException e) {
                log.warn("Read dir error: {}", svcDir);
            }
        }

        if (results.isEmpty()) {
            throw new NotFoundException("No matching datasets found.");
        }

        results.sort((a, b) -> {
            java.time.Instant ia = (java.time.Instant) a.get("lastModified");
            java.time.Instant ib = (java.time.Instant) b.get("lastModified");
            return ib.compareTo(ia);
        });

        return Map.of("status", "success", "serviceName", serviceName,
                "count", results.size(), "datasetFiles", results);
    }

    public Path findDatasetFile(String serviceName, String fileName) {
        if (!FilePathGuard.isSafeFileName(serviceName) || !FilePathGuard.isSafeFileName(fileName)) {
            throw new BadRequestException("Invalid serviceName or fileName.");
        }
        for (Path root : datasetRoots()) {
            Path candidate = root.resolve(serviceName).resolve(fileName);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new NotFoundException("File not found.");
    }

    public Map<String, String> deleteDataset(String serviceName, String fileName) {
        Path file = findDatasetFile(serviceName, fileName);
        try {
            Files.delete(file);
        } catch (IOException e) {
            log.error("Delete failed for {}: {} - {}", file, e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("Failed to delete file: " + e.getMessage());
        }
        return Map.of("status", "success", "message", "File deleted successfully.",
                "serviceName", serviceName, "fileName", fileName);
    }
}
