package net.benjaminurquhart.diannex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import net.benjaminurquhart.diannex.DNXBytecode.Opcode;

public class DNXAssembler {
	
	public static class AssembleException extends RuntimeException {
		
		private static final long serialVersionUID = 3508605948693042214L;
		
		public AssembleException(String reason, Throwable cause) {
			super(reason, cause);
		}
	}

	public static List<DNXBytecode> assemble(String asm, DNXFile reader) {
		List<DNXBytecode> out = new ArrayList<>();
		String[] tokens;
		Opcode opcode;
		for(String line : asm.split("\r?\n")) {
			if(line.startsWith(";") || line.isBlank()) {
				continue;
			}
			try {
				tokens = parseTokens(line);
				if(tokens.length == 0) {
					continue;
				}
				opcode = Opcode.valueOf(tokens[0].toUpperCase());
				out.add(new DNXBytecode(reader, opcode, (Object[])Arrays.copyOfRange(tokens, 1, tokens.length)));
			}
			catch(Exception e) {
				throw new AssembleException("Asssembly failed", e);
			}
		}
		return out;
	}
	
	private static String[] parseTokens(String line) {
		List<String> out = new ArrayList<>();
		
		StringBuilder token = new StringBuilder();
		boolean inString = false, escaped = false, unescape = false;
		
		Consumer<Boolean> append = force -> {
			if(force || token.length() > 0) {
				out.add(token.toString());
				if(token.length() > 0) {
					token.delete(0, token.length());
				}
					
			}
		};
		
		for(char c : line.toCharArray()) {
			if(escaped) {
				if(unescape) {
					unescape = false;
					escaped = false;
				}
				else {
					unescape = true;
					token.append(c);
					continue;
				}
			}
			if(c == ';' && !inString) {
				break;
			}
			else if(c == '"') {
				append.accept(inString);
				inString = !inString;
			}
			else if(c == '\\') {
				escaped = true;
			}
			else if(c == ' ' && !inString) {
				append.accept(false);
			}
			else {
				token.append(c);
			}
		}
		if(inString) {
			throw new IllegalStateException("unclosed string: " + line);
		}
		if(escaped && !unescape) {
			throw new IllegalStateException("trailing unescaped backslash: " + line + " (raw newlines are not supported, use \\n)");
		}
		append.accept(false);
		return out.toArray(String[]::new);
	}
}
