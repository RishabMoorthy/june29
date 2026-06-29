package com.stubserver.backend.api;

import com.stubserver.backend.service.ServerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/backend/api")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;

    @GetMapping("/getServerLists")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<List<Map<String, Object>>> getServerLists() {
        return ResponseEntity.ok(serverService.getServerLists());
    }

    @PostMapping("/run-batch")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Map<String, String>> runBatch(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(serverService.runBatch(body.get("action"), body.get("servername")));
    }

    @GetMapping("/getLiveStatus")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Map<String, Object>> getLiveStatus(HttpServletRequest request) {
        return ResponseEntity.ok(serverService.getLiveStatus(request));
    }

    @GetMapping("/serverTimeInfo")
    @PreAuthorize("hasAnyRole('Admin','ApplicationUser','Guest')")
    public ResponseEntity<Map<String, String>> serverTimeInfo() {
        return ResponseEntity.ok(serverService.serverTimeInfo());
    }

    // Public — called by the UI on the login page, before any token exists.
    @GetMapping("/getServerIP")
    public ResponseEntity<Map<String, String>> getServerIP() {
        return ResponseEntity.ok(serverService.getServerIp());
    }
}
