package com.ai.learning.dify.controller;

import com.ai.learning.dify.model.ToolRequest;
import com.ai.learning.dify.model.ToolResponse;
import com.ai.learning.dify.service.WeatherService;
import com.ai.learning.dify.service.CalculatorService;
import com.ai.learning.dify.service.DateTimeService;
import com.ai.learning.dify.service.MemoryNoteService;
import org.springframework.web.bind.annotation.*;

/**
 * Java 工具 API —— 供 Dify 作为自定义工具调用。
 * Dify 通过 HTTP POST 调用这些端点。
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final WeatherService weatherService;
    private final CalculatorService calculatorService;
    private final DateTimeService dateTimeService;
    private final MemoryNoteService memoryNoteService;

    public ToolController(WeatherService weatherService,
                          CalculatorService calculatorService,
                          DateTimeService dateTimeService,
                          MemoryNoteService memoryNoteService) {
        this.weatherService = weatherService;
        this.calculatorService = calculatorService;
        this.dateTimeService = dateTimeService;
        this.memoryNoteService = memoryNoteService;
    }

    @PostMapping("/weather")
    public ToolResponse weather(@RequestBody ToolRequest req) {
        try {
            return ToolResponse.ok(weatherService.getWeather(req.getQuery()));
        } catch (Exception e) {
            return ToolResponse.fail("天气查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/calculator")
    public ToolResponse calculator(@RequestBody ToolRequest req) {
        try {
            return ToolResponse.ok(calculatorService.calculate(req.getQuery()));
        } catch (Exception e) {
            return ToolResponse.fail("计算失败: " + e.getMessage());
        }
    }

    @PostMapping("/datetime")
    public ToolResponse datetime(@RequestBody ToolRequest req) {
        try {
            return ToolResponse.ok(dateTimeService.getInfo(req.getQuery()));
        } catch (Exception e) {
            return ToolResponse.fail("时间查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/note")
    public ToolResponse note(@RequestBody ToolRequest req) {
        try {
            return ToolResponse.ok(memoryNoteService.handle(req.getQuery(), req.getParams()));
        } catch (Exception e) {
            return ToolResponse.fail("笔记操作失败: " + e.getMessage());
        }
    }

    // ─── OpenAPI 规范（供 Dify 自动发现工具） ───
    @GetMapping("/openapi.json")
    public String openApiSpec() {
        return """
{
  "openapi": "3.0.0",
  "info": { "title": "Java Tools API", "version": "1.0.0" },
  "servers": [{"url": "http://localhost:8080"}],
  "paths": {
    "/api/tools/weather": {
      "post": {
        "summary": "\u67e5\u8be2\u5929\u6c14",
        "description": "\u67e5\u8be2\u6307\u5b9a\u57ce\u5e02\u7684\u5929\u6c14\u60c5\u51b5",
        "requestBody": {
          "content": {
            "application/json": {
              "schema": {
                "type": "object",
                "properties": {
                  "query": {"type": "string", "description": "\u57ce\u5e02\u540d\uff0c\u5982\uff1a\u5317\u4eac"}
                }
              }
            }
          }
        },
        "responses": {"200": {"description": "\u5929\u6c14\u4fe1\u606f"}}
      }
    },
    "/api/tools/calculator": {
      "post": {
        "summary": "\u6570\u5b66\u8ba1\u7b97",
        "description": "\u6267\u884c\u6570\u5b66\u8868\u8fbe\u5f0f\u8ba1\u7b97",
        "requestBody": {
          "content": {
            "application/json": {
              "schema": {
                "type": "object",
                "properties": {
                  "query": {"type": "string", "description": "\u6570\u5b66\u8868\u8fbe\u5f0f\uff0c\u5982\uff1a25*40"}
                }
              }
            }
          }
        },
        "responses": {"200": {"description": "\u8ba1\u7b97\u7ed3\u679c"}}
      }
    },
    "/api/tools/datetime": {
      "post": {
        "summary": "\u65e5\u671f\u65f6\u95f4",
        "description": "\u83b7\u53d6\u5f53\u524d\u65e5\u671f\u3001\u65f6\u95f4\u6216\u65e5\u671f\u8ba1\u7b97",
        "requestBody": {
          "content": {
            "application/json": {
              "schema": {
                "type": "object",
                "properties": {
                  "query": {"type": "string", "description": "\u67e5\u8be2\u5185\u5bb9"}
                }
              }
            }
          }
        },
        "responses": {"200": {"description": "\u65f6\u95f4\u4fe1\u606f"}}
      }
    },
    "/api/tools/note": {
      "post": {
        "summary": "\u7b14\u8bb0\u7ba1\u7406",
        "description": "\u4fdd\u5b58\u6216\u8bfb\u53d6\u7b14\u8bb0",
        "requestBody": {
          "content": {
            "application/json": {
              "schema": {
                "type": "object",
                "properties": {
                  "query": {"type": "string", "description": "\u64cd\u4f5c\uff1asave/read/list"},
                  "params": {"type": "string", "description": "\u7b14\u8bb0\u5185\u5bb9\u6216\u5173\u952e\u8bcd"}
                }
              }
            }
          }
        },
        "responses": {"200": {"description": "\u64cd\u4f5c\u7ed3\u679c"}}
      }
    }
  }
}
""";
    }
}
