package com.ai.cs.ticket;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Ticket REST controller.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** Create ticket */
    @PostMapping
    public ResponseEntity<Ticket> create(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String description = body.get("description");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String category = body.getOrDefault("category", "其他");
        Ticket.Priority priority = parsePriority(body.getOrDefault("priority", "MEDIUM"));
        String creatorName = body.getOrDefault("creatorName", "匿名用户");

        Ticket ticket = ticketService.createTicket(title, description, category, priority, creatorName);

        // Optional: assign from chat session
        if (body.containsKey("sessionId")) {
            ticket.setSessionId(body.get("sessionId"));
        }
        if (body.containsKey("creatorContact")) {
            ticket.setCreatorContact(body.get("creatorContact"));
        }

        return ResponseEntity.ok(ticket);
    }

    /** List tickets with optional filters */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String assignee) {

        Ticket.Status s = status != null ? Ticket.Status.valueOf(status.toUpperCase()) : null;
        Ticket.Priority p = priority != null ? Ticket.Priority.valueOf(priority.toUpperCase()) : null;

        List<Ticket> results = ticketService.listTickets(s, p, category, assignee);
        return ResponseEntity.ok(Map.of(
                "total", results.size(),
                "tickets", results
        ));
    }

    /** Get single ticket */
    @GetMapping("/{id}")
    public ResponseEntity<Ticket> get(@PathVariable String id) {
        Ticket ticket = ticketService.getTicket(id);
        if (ticket == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ticket);
    }

    /** Assign ticket */
    @PutMapping("/{id}/assign")
    public ResponseEntity<?> assign(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            String assignee = body.getOrDefault("assignee", "未分配");
            Ticket ticket = ticketService.assignTicket(id, assignee);
            return ResponseEntity.ok(ticket);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Update ticket status */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id,
                                           @RequestBody Map<String, String> body) {
        try {
            Ticket.Status newStatus = Ticket.Status.valueOf(
                    body.getOrDefault("status", "PENDING").toUpperCase());
            String resolution = body.get("resolution");
            Ticket ticket = ticketService.updateStatus(id, newStatus, resolution);
            return ResponseEntity.ok(ticket);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Delete ticket */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }

    /** Get statistics */
    @GetMapping("/stats")
    public ResponseEntity<TicketService.TicketStats> stats() {
        return ResponseEntity.ok(ticketService.getStats());
    }

    /** Get categories */
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> categories() {
        return ResponseEntity.ok(Map.of(
                "categories", ticketService.getCategories(),
                "statuses", Ticket.Status.values(),
                "priorities", Ticket.Priority.values()
        ));
    }

    /** Auto-create ticket from chat (AI detects issues) */
    @PostMapping("/from-chat")
    public ResponseEntity<Ticket> fromChat(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "客服对话转工单");
        String description = body.getOrDefault("description", "来自客服对话");
        String sessionId = body.get("sessionId");
        String creatorName = body.getOrDefault("creatorName", "客服系统");
        String category = body.getOrDefault("category", "其他");
        Ticket.Priority priority = parsePriority(body.getOrDefault("priority", "MEDIUM"));

        Ticket ticket = ticketService.createTicket(title, description, category, priority, creatorName);
        if (sessionId != null) ticket.setSessionId(sessionId);
        return ResponseEntity.ok(ticket);
    }

    private Ticket.Priority parsePriority(String p) {
        try {
            return Ticket.Priority.valueOf(p.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Ticket.Priority.MEDIUM;
        }
    }
}
