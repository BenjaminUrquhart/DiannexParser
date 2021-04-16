package net.benjaminurquhart.diannex;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.benjaminurquhart.diannex.runtime.ANSI;
import net.benjaminurquhart.diannex.runtime.DNXRuntime;
import net.benjaminurquhart.diannex.runtime.ExternalDNXFunction;
import net.benjaminurquhart.diannex.runtime.ExternalFunction;
import net.benjaminurquhart.diannex.runtime.RuntimeContext;
import net.benjaminurquhart.diannex.runtime.Value;

public class Main {
	
	public static class TestFunctions {
		
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
		
		@ExternalDNXFunction("_temDoAttack")
		public static void temDoAttack(RuntimeContext context) {
			Value attackDodgeVal = context.globalVars.get("temp_attackDodgeValue");
			Value attackHit = context.globalVars.get("temp_attackHit");
			
			// Get hit by second bullet
			attackDodgeVal.update(attackDodgeVal.get(int.class) + 1);
			attackHit.update(attackHit.get(int.class) + 1);
		}
	}

	
	public static void main(String[] args) throws Exception {
		System.setErr(System.out);
		System.out.print(ANSI.RESET);
		
		DNXFile file = new DNXFile(new File("tsus-1.00/data/game_orig.dxb"));
		
		DNXRuntime runtime = new DNXRuntime(file);
		runtime.getContext().registerExternalFunctions(ExternalFunction.getFrom(TestFunctions.class));
		
		runtime.getContext().setMissingExternalFunctionHandler((name, arguments) -> {
			System.out.printf("%sFunction stub: %s(args=%s)%s\n", ANSI.GRAY, name, Arrays.deepToString(arguments), ANSI.RESET);
			return 0;
		});
		
		System.out.println("Return value: " + runtime.eval(file.sceneByName("tem.battle")));
	}
}

