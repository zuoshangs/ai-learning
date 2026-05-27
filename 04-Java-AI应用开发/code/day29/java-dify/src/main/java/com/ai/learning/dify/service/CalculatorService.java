package com.ai.learning.dify.service;

import org.springframework.stereotype.Service;
import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;

@Service
public class CalculatorService {
    private final ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");

    public String calculate(String expression) throws Exception {
        // 支持简单数学运算
        Object result = engine.eval(expression);
        return expression + " = " + result;
    }
}