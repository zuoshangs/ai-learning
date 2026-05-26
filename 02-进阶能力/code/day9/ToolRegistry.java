// ToolRegistry.java — 工具注册和执行器（Java 版）
// 编译: javac ToolRegistry.java
// 运行: java ToolRegistry  （执行测试）

import java.util.*;
import java.util.function.Function;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ToolRegistry {

    // ─── 工具定义 ─────────────────────────────
    static class ToolDef {
        String name;
        String description;
        Function<Map<String, Object>, Map<String, Object>> handler;

        ToolDef(String name, String description, Function<Map<String, Object>, Map<String, Object>> handler) {
            this.name = name;
            this.description = description;
            this.handler = handler;
        }
    }

    private final Map<String, ToolDef> tools = new LinkedHashMap<>();

    public void register(String name, String description,
                         Function<Map<String, Object>, Map<String, Object>> handler) {
        tools.put(name, new ToolDef(name, description, handler));
    }

    public Map<String, Object> execute(String name, Map<String, Object> args) {
        ToolDef tool = tools.get(name);
        if (tool == null) {
            return Map.of("error", "未知工具: " + name);
        }
        try {
            return tool.handler.apply(args);
        } catch (Exception e) {
            return Map.of("error", "工具执行失败: " + e.getMessage());
        }
    }

    public List<String> listTools() {
        return new ArrayList<>(tools.keySet());
    }

    // ─── ====== 工具实现 ====== ───────────────

    // 天气数据
    static final Map<String, Map<String, Object>> WEATHER_DATA = Map.of(
        "北京", Map.of("temp", 25, "condition", "晴", "humidity", 45, "wind", "3级"),
        "上海", Map.of("temp", 28, "condition", "多云", "humidity", 65, "wind", "4级"),
        "广州", Map.of("temp", 32, "condition", "阵雨", "humidity", 80, "wind", "2级"),
        "深圳", Map.of("temp", 31, "condition", "多云", "humidity", 75, "wind", "3级"),
        "杭州", Map.of("temp", 27, "condition", "小雨", "humidity", 70, "wind", "3级"),
        "成都", Map.of("temp", 26, "condition", "阴", "humidity", 60, "wind", "2级")
    );

    static final Map<String, String> ADVICE = Map.of(
        "晴", "适合户外活动，注意防晒 ☀️",
        "多云", "天气不错，适宜出行 ⛅",
        "阴", "天气阴沉，建议带伞 🌥️",
        "小雨", "建议带伞，路面湿滑注意安全 🌦️",
        "阵雨", "可能有阵雨，随身带伞 🌧️"
    );

    // 工具1: 天气查询
    Map<String, Object> getWeather(Map<String, Object> args) {
        String city = (String) args.getOrDefault("city", "北京");
        String date = (String) args.getOrDefault("date",
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

        Map<String, Object> base = WEATHER_DATA.getOrDefault(city,
            Map.of("temp", 20, "condition", "未知", "humidity", 50, "wind", "2级"));

        int seed = Math.abs((city + "_" + date).hashCode() % 100);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("date", date);
        result.put("temperature", ((int) base.get("temp") + (seed % 7 - 3)) + "°C");
        result.put("condition", base.get("condition"));
        result.put("wind", base.get("wind"));
        result.put("advice", ADVICE.getOrDefault(base.get("condition"), "注意关注实时预报"));
        return result;
    }

    // 工具2: 计算器
    Map<String, Object> calculator(Map<String, Object> args) {
        String expression = (String) args.getOrDefault("expression", "");

        // 安全检查
        if (expression.contains("__") || expression.contains("exec") ||
            expression.contains("eval") || expression.contains("Runtime")) {
            return Map.of("error", "包含非法内容", "expression", expression);
        }

        try {
            // Java 没有内置的 eval（安全考虑），这里用 JavaScript 引擎
            javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = mgr.getEngineByName("JavaScript");
            Object result = engine.eval(expression);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("expression", expression);
            res.put("result", result);
            res.put("formatted", expression + " = " + result);
            return res;
        } catch (Exception e) {
            return Map.of("error", e.getMessage(), "expression", expression);
        }
    }

    // ─── 测试 ────────────────────────────────────
    public static void main(String[] args) {
        ToolRegistry registry = new ToolRegistry();

        // 注册工具
        ToolRegistry self = registry;
        registry.register("get_weather", "获取指定城市的天气信息",
            arg -> self.getWeather(arg));
        registry.register("calculator", "执行数学计算",
            arg -> self.calculator(arg));

        System.out.println("📦 已注册 " + registry.listTools().size() + " 个工具: "
            + String.join(", ", registry.listTools()));

        // 测试天气
        System.out.println("\n🔧 测试 get_weather(北京)");
        Map<String, Object> r = registry.execute("get_weather", Map.of("city", "北京"));
        System.out.println("   " + r.get("temperature") + ", " + r.get("condition"));

        // 测试计算器
        System.out.println("\n🔧 测试 calculator(123456 * 789012)");
        r = registry.execute("calculator", Map.of("expression", "123456 * 789012"));
        System.out.println("   " + r.get("formatted"));

        // 测试未注册工具
        System.out.println("\n🔧 测试未知工具");
        r = registry.execute("unknown_tool", Map.of());
        System.out.println("   " + r.get("error"));
    }
}
