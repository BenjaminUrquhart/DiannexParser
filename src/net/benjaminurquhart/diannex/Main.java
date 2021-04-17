package net.benjaminurquhart.diannex;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.benjaminurquhart.diannex.runtime.ANSI;
import net.benjaminurquhart.diannex.runtime.DNXRuntime;
import net.benjaminurquhart.diannex.runtime.ExternalDNXFunction;
import net.benjaminurquhart.diannex.runtime.ExternalFunction;
import net.benjaminurquhart.diannex.runtime.RuntimeContext;

public class Main {
	
	public static class TSUSFunctions {
		
		private static Map<String, Integer> flags = new HashMap<>(), persistFlags = new HashMap<>();
		
		@ExternalDNXFunction
		public static void setFlag(String flag, int val) {
			flags.put(flag, val);
		}
		
		@ExternalDNXFunction
		public static int getFlag(String flag) {
			return flags.computeIfAbsent(flag, f -> 0);
		}
		
		@ExternalDNXFunction
		public static void setPersistFlag(String flag, int val) {
			persistFlags.put(flag, val);
		}
		
		@ExternalDNXFunction
		public static int getPersistFlag(String flag) {
			return persistFlags.computeIfAbsent(flag, f -> 0);
		}
	}

	
	public static void main(String[] args) throws Exception {
		System.out.print(ANSI.RESET);
		
		DNXFile file = new DNXFile(new File("tsus-1.00/data/game_orig.dxb"));
		
		DNXRuntime runtime = new DNXRuntime(file);
		RuntimeContext context = runtime.getContext();
		
		context.makeHeadless();
		context.registerExternalFunctions(ExternalFunction.getFrom(TSUSFunctions.class));
		
		/*
		context.setMissingExternalFunctionHandler((name, arguments) -> {
			System.out.printf("%sFunction stub: %s(args=%s)%s\n", ANSI.GRAY, name, Arrays.deepToString(arguments), ANSI.RESET);
			return 0;
		});*/
	
		
		for(DNXScene scene : file.getScenes()) {
			context.reset();
			System.out.println("Executing " + scene);
			System.out.println("Return value: " + runtime.eval(scene));
		}
	}
}

