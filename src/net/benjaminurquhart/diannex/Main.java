package net.benjaminurquhart.diannex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.io.Files;

import net.benjaminurquhart.diannex.runtime.*;

public class Main {
	
	public static boolean WAIT_FOR_INPUT = true;
	
	public static boolean OFFSET_LOOKUP = false;
	public static boolean DUMP_ORDER = false;
	
	public static void main(String[] args) throws Exception {
		
		System.out.print(ANSI.RESET);
		
		File data = new File("game.dxb");
		DNXFile file = new DNXFile(data);
		
		//dump(file);
		
		if(OFFSET_LOOKUP) {
			int offset = 0;
			Map<Integer, DNXBytecode> offsetMapping = new HashMap<>();
			List<Integer> offsets = new ArrayList<>();
			for(DNXBytecode entry : file.getBytecode()) {
				offsetMapping.put(offset, entry);
				offsets.add(offset);
				
				offset += entry.getLength();
			}
			Scanner sc = new Scanner(System.in);
			DNXCompiled owned = null;
			DNXBytecode entry;
			while(true) {
				try {
					System.out.print("Offset: ");
					offset = sc.nextInt();
					entry = offsetMapping.get(offset);
					if(entry == null) {
						System.out.println("Unmapped");
						for(int i = 0, o1, o2; i < offsets.size() - 1; i++) {
							o1 = offsets.get(i);
							o2 = offsets.get(i + 1);
							
							if(o2 > offset && o1 < offset) {
								System.out.println("Aligning to " + o1);
								entry = offsetMapping.get(o1);
								break;
							}
						}
					}
					owned = null;
					for(DNXCompiled e : file.getEntries()) {
						if(e.instructions.contains(entry)) {
							owned = e;
							break;
						}
					}
					System.out.printf("%s (%s)\n", owned, entry);
				}
				catch(Exception e) {
					break;
				}
			}
			sc.close();
			return;
		}
		
		if(DUMP_ORDER) {
			Map<Integer, String> names = new HashMap<>();
			for(DNXCompiled entry : file.getEntries()) {
				for(int index : entry.bytecodeIndicies) {
					if(index != -1) {
						names.put(index, entry.name.get());
					}
				}
			}
			names.keySet().stream().sorted().map(i -> names.get(i) + " (" + i + ")").forEach(System.out::println);
			return;
		}
		
		DNXScene scene = file.sceneByName("stars.cb_battle_g");
		
		Pattern pattern = Pattern.compile("(`([^`]+)`)", Pattern.CASE_INSENSITIVE);
		
		DNXRuntime runtime = new DNXRuntime(file);
		RuntimeContext context = runtime.getContext();
		
		context.setVerbose(false);
		context.autodefineGlobals(true);
		context.registerExternalFunction(new ExternalFunction((Function<String, File>)(filename) -> new File(filename), "openFile"));
		//context.registerExternalFunctions(ExternalFunction.getFrom(TSUSActorStubs.class));
		context.registerExternalFunctions(ExternalFunction.getFrom(TSUSFunctions.class));
		
		context.setMissingExternalFunctionHandler((name, arguments) -> {
			System.out.printf("%sFunction stub: %s(args=%s)%s\n", ANSI.GRAY, name, Arrays.deepToString(arguments), ANSI.RESET);
			return 0;
		});
		
		TSUSFunctions.setPersistFlag("mgd_43", 7);
		//TSUSFunctions.setFlag("st_kk_outfit", -1);
		//TSUSFunctions.setFlag("plot", 58);
		
		context.setTextrunHandler((ctx, text) -> {
			//System.out.printf("%s%s%s\n", ANSI.GRAY, text, ANSI.RESET);
			text = text.replace("\\", "").replace("#", "\n");
			Matcher matcher = pattern.matcher(text);
			char code;
			String toReplace, group, replace = "";
			
			while(matcher.find()) {
				replace = "";
				
				toReplace = matcher.group(1);
				group = matcher.group(2);
				code = group.charAt(0);
				
				//System.out.println(toReplace + " " + group);
				
				switch(code) {
				case 'c': replace = ANSI.getColorFrom(group.charAt(1)).toString(); break;
				case 'i':
				case 'e':
				case '1':
				case 'p': break;
				default: replace = group;
				}
				text = text.replace(toReplace, replace);
			}
			if(!text.startsWith("\n") && text.contains("\n")) {
				text = "\n" + text;
			}
			System.out.printf("[%s] %s", ctx.getTyper(), text);
			if(context.isChoicing()) {
				System.out.println();
				return;
			}
			if(WAIT_FOR_INPUT) {
				context.waitForInput();
			}
			else {
				System.out.println();
			}
		});
		
		System.out.println("Return value: " + runtime.eval(scene).get());
		//System.out.println("Return value: " + runtime.eval(scene).get());
	}
	
	public static void dump(DNXFile file) throws IOException {
		File root = new File("output");
		disassembleAll(file, file.getScenes(), root, "scenes");
		disassembleAll(file, file.getFunctions(), root, "functions");
		disassembleAll(file, file.getDefinitions(), root, "definitions");
	}
	
	public static void disassembleAll(DNXFile file, List<? extends DNXCompiled> list, File root, String folderName) throws IOException {
		File folder = new File(root, folderName);
		
		if(!folder.exists()) {
			folder.mkdirs();
		}
		
		for(DNXCompiled entry : list) {
			Files.write(entry.disassemble(file).getBytes(), new File(folder, entry.name.get() + ".asm"));
		}
	}
	
	
	public static class TSUSFunctions {
		
		private static Map<String, Integer> flags = new HashMap<>(), persistFlags = new HashMap<>();
		
		public static boolean isGeno, isEvac, skipWait = false;
		
		@ExternalDNXFunction
		public static int itemsGetArmor() {
			return 0;
		}
		
		@ExternalDNXFunction("xirandom")
		public static int randomRange(int lower, int upper) {
			return (int)((upper - lower + 1) * Math.random()) + lower;
		}
		
		@VarArg
		@ExternalDNXFunction
		public static void wait(int seconds, boolean skippable) throws Exception {
			if(!skipWait) Thread.sleep(seconds * 1000);
		}
		
		@VarArg
		@ExternalDNXFunction
		public static void waitFrames(int frames, boolean skippable) throws Exception {
			if(!skipWait) Thread.sleep((int)(frames * 33.3333));
		}
		
		@ExternalDNXFunction
		public static synchronized void setFlag(String flag, int val) {
			System.out.printf("%sSet flag: %s = %s%s\n", ANSI.GRAY, flag, val, ANSI.RESET);
			flags.put(flag, val);
		}
		
		@ExternalDNXFunction
		public static int getFlag(String flag) {
			int val = flags.computeIfAbsent(flag, f -> 0);
			System.out.printf("%sGet flag: %s = %s%s\n", ANSI.GRAY, flag, val, ANSI.RESET);
			return val;
		}
		
		@ExternalDNXFunction("setPersistFlag")
		public static synchronized void setPersistFlag(String flag, int val) {
			System.out.printf("%sSet persistent flag: %s = %s%s\n", ANSI.GRAY, flag, val, ANSI.RESET);
			persistFlags.put(flag, val);
		}
		
		@ExternalDNXFunction("getPersistFlag")
		public static int getPersistFlag(String flag) {
			int val = persistFlags.computeIfAbsent(flag, f -> 0);
			System.out.printf("%sGet persistent flag: %s = %s%s\n", ANSI.GRAY, flag, val, ANSI.RESET);
			return val;
		}
		
		@ExternalDNXFunction("goto")
		public static Value sceneGoto(RuntimeContext context, String name) {
			DNXScene scene = context.file.sceneByName(name);
			if(scene == null) {
				throw new IllegalArgumentException("No such scene: " + name);
			}
			try {
				System.out.printf("%sGoto: %s%s\n", ANSI.GRAY, scene, ANSI.RESET);
				Map<Integer, Value> oldLocalVars = context.localVars;
				ValueStack oldStack = context.stack;
				context.localVars = new HashMap<>();
				context.stack = new ValueStack();
				Value retVal =  context.runtime.eval(scene).get();
				context.localVars = oldLocalVars;
				context.stack = oldStack;
				return retVal;
			}
			catch(RuntimeException e) {
				throw e;
			}
			catch(Throwable e) {
				throw new RuntimeException(e);
			}
		}
		
		@ExternalDNXFunction
		public static void runSceneParallel(RuntimeContext context, String name) {
			DNXRuntime runtime = new DNXRuntime(context.file);
			RuntimeContext ctx = runtime.getContext();
			ctx.globalVars = context.globalVars;
			ctx.isParallelScene = true;
			ctx.parent = context;
			
			ctx.setMissingExternalFunctionHandler((n, arguments) -> {
				System.out.printf("%s%s", ANSI.GRAY, ctx.parallelString());
				return context.getMissingExternalFunctionHandler().apply(n, arguments);
			});
			ctx.autodefineGlobals(context.autodefineGlobals());
			ctx.setVerbose(context.isVerbose());
			
			context.getExternalFunctions()
				   .stream()
				   .filter(f -> !f.getName().equals("char"))
				   .forEach(ctx::registerExternalFunction);
			
			ctx.setTextrunHandler((c, txt) -> {
				throw new UnsupportedOperationException("cannot textrun from parallel scene");
			});
			
			runtime.eval(context.file.sceneByName(name));
		}
		
		@ExternalDNXFunction("temTriggerBattle")
		public static Value enterTemBattle(RuntimeContext context) {
			return sceneGoto(context, "tem.battle");
		}
		
		@ExternalDNXFunction("ruthPossible")
		public static boolean isGeno() {
			System.out.printf("%sisGeno: %s%s\n", ANSI.GRAY, isGeno, ANSI.RESET);
			return isGeno;
		}
		
		@ExternalDNXFunction("evac")
		public static boolean isEvac(int area) {
			System.out.printf("%sisEvac(%d) -> %s%s\n", ANSI.GRAY, area, isEvac, ANSI.RESET);
			return isEvac;
		}
	}

	
}



