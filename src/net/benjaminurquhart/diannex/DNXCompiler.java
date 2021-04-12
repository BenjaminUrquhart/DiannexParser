package net.benjaminurquhart.diannex;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

public class DNXCompiler {
	
	public static class CompileException extends RuntimeException {

		private static final long serialVersionUID = -6077185586807638257L;
		
		public CompileException(String reason, Throwable cause) {
			super(reason, cause);
		}
	}
	
	public static class CompileOutput {
		public final List<DNXDefinition> definitions;
		public final List<DNXFunction> functions;
		public final List<DNXString> strings;
		public final List<DNXScene> scenes;
		
		public final DNXFile compileFile;
		
		protected CompileOutput(String code) {
			try {
				File tmpFile = File.createTempFile("compile", ".dnx");
				Files.write(tmpFile.toPath(), code.getBytes("utf-8"));
				
				File output = new File("out/out.dxb");
				if(output.exists()) {
					output.delete();
				}
				
				Process process = Runtime.getRuntime().exec("./diannex --cli --files \"" + tmpFile.getAbsolutePath() + "\"");
				int exitCode = process.waitFor();
				
				InputStream out = process.getInputStream(), err = process.getErrorStream();
				
				while(out.available() > 0) {
					System.out.write(out.read());
				}
				while(err.available() > 0) {
					System.err.write(err.read());
				}
				
				System.out.flush();
				System.err.flush();
				
				System.out.println();
				System.err.println();
				
				if(!output.exists() || exitCode != 0) {
					throw new CompileException("Compilation failed with exit code " + exitCode, null);
				}
				
				DNXFile file = new DNXFile(output);
				compileFile = file;
				
				definitions = file.getDefinitions();
				functions = file.getFunctions();
				strings = file.getStrings();
				scenes = file.getScenes();
			}
			catch(CompileException e) {
				throw e;
			}
			catch(Exception e) {
				throw new CompileException("Compilation failed", e);
			}
		}
	}

	public static CompileOutput compileFull(String code) {
		return new CompileOutput(code);
	}
	
	public static List<DNXBytecode> compile(String code, DNXFile reader) {
		CompileOutput output = compileFull("namespace tmp { scene tmpScene {" + code + "}}");
		String asm = output.scenes.get(0).disassemble(output.compileFile);
		
		return DNXAssembler.assemble(asm, reader);
	}
}
