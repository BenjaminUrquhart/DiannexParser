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
		Object[] args = new Object[2];
		String[] tokens;
		Opcode opcode;
		for(String line : asm.split("\r?\n")) {
			if(line.startsWith(";") || line.isBlank()) {
				continue;
			}
			tokens = parseTokens(line);
			if(tokens.length == 0) {
				continue;
			}
			else if(tokens.length > 3) {
				throw new AssembleException("Too many arguments provided; expected at most 2, got " + (tokens.length - 1) + " (inst: " + line + ", tokens: " + Arrays.toString(tokens) + ")", null);
			}
			try {
				opcode = Opcode.valueOf(tokens[0].toUpperCase());
				for(int i = 1; i < tokens.length; i++) {
					args[i-1] = tokens[i];
				}
				out.add(new DNXBytecode(reader, opcode, args));
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
				}
			}
			if(c == ';' && !inString) {
				break;
			}
			else if(c == '"' && !escaped) {
				append.accept(inString);
				inString = !inString;
			}
			else if(c == '\\') {
				escaped = true;
			}
			else if(c == ' ' && !inString && !escaped) {
				append.accept(false);
			}
			else {
				token.append(c);
			}
		}
		append.accept(false);
		return out.toArray(String[]::new);
	}
}
