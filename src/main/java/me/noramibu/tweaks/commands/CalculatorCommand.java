package me.noramibu.tweaks.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.commands.SharedSuggestionProvider;

public class CalculatorCommand extends Command {
    public CalculatorCommand() {
        super("calculator", "Evaluates a mathematical expression.", "calc");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void build(LiteralArgumentBuilder builder) {
        builder.executes(ctx -> {
            error("Usage: .calculator <expression>");
            return SINGLE_SUCCESS;
        });

        builder.then(argument("expression", StringArgumentType.greedyString())
            .executes(ctx -> {
                String expression = ctx.getArgument("expression", String.class);

                try {
                    double result = new ExpressionParser(expression).parse();
                    if (Double.isNaN(result)) {
                        error("Result is not a number.");
                    } else if (Double.isInfinite(result)) {
                        error("Result is infinite.");
                    } else {
                        info("%s = (highlight)%s(default)", expression, format(result));
                    }
                } catch (IllegalArgumentException e) {
                    error("%s", e.getMessage());
                }

                return SINGLE_SUCCESS;
            }));
    }

    private String format(double value) {
        if (Math.abs(value) < 1e-10) return "0";
        if (Math.abs(value - Math.rint(value)) < 1e-10) return Long.toString(Math.round(value));
        return Double.toString(value);
    }

    private static class ExpressionParser {
        private final String input;
        private int pos;

        private ExpressionParser(String input) {
            this.input = input;
        }

        public double parse() {
            double result = parseExpression();
            skipWhitespace();
            if (pos < input.length()) {
                throw error("Unexpected character '%s' at position %d.", input.charAt(pos), pos + 1);
            }
            return result;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    break;
                }
            }
            return value;
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value *= parseFactor();
                } else if (match('/')) {
                    double divisor = parseFactor();
                    if (Math.abs(divisor) < 1e-12) throw error("Division by zero.");
                    value /= divisor;
                } else {
                    break;
                }
            }
            return value;
        }

        private double parseFactor() {
            double base = parseUnary();
            skipWhitespace();
            if (match('^')) {
                double exponent = parseFactor();
                base = Math.pow(base, exponent);
            }
            return base;
        }

        private double parseUnary() {
            skipWhitespace();
            if (match('+')) return parseUnary();
            if (match('-')) return -parseUnary();
            return parsePrimary();
        }

        private double parsePrimary() {
            skipWhitespace();
            if (match('(')) {
                double value = parseExpression();
                if (!match(')')) throw error("Missing closing parenthesis.");
                return value;
            }

            int start = pos;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (Character.isDigit(c) || c == '.') pos++;
                else break;
            }

            if (start == pos) {
                if (pos >= input.length()) throw error("Unexpected end of expression.");
                throw error("Unexpected character '%s' at position %d.", input.charAt(pos), pos + 1);
            }

            try {
                return Double.parseDouble(input.substring(start, pos));
            } catch (NumberFormatException e) {
                throw error("Invalid number at position %d.", start + 1);
            }
        }

        private boolean match(char expected) {
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
        }

        private IllegalArgumentException error(String message, Object... args) {
            return new IllegalArgumentException(String.format(message, args));
        }
    }
}
