package net.benjaminurquhart.diannex;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Set;

import com.google.common.io.LittleEndianDataOutputStream;

public class DNXBytecode implements IDNXSerializable {
	
	public static enum Type {
		DEFAULT,
		SINGLE,
		DOUBLE,
		FLOAT;
	}
	
	public static enum Opcode {
		NOP(0x00),
		
		FREELOC(0x0A),
		
		SAVE(0x0B),
		LOAD(0x0C),
		
		PUSHU(0x0F),
		PUSHI(0x10),
		PUSHD(0x11),
		
		PUSHS(0x12),
		PUSHINTS(0x13),
		PUSHBS(0x14),
		PUSHBINTS(0x15),
		
		MAKEARR(0x16),
		PUSHARRIND(0x17),
		SETARRIND(0x18),
		
		SETVARGLB(0x19),
		SETVARLOC(0x1A),
		PUSHVARGLB(0x1B),
		PUSHVARLOC(0x1C),
		
		POP(0x1D),
		DUP(0x1E),
		DUP2(0x1F),
		
		ADD(0x20),
		SUB(0x21),
		MUL(0x22),
		DIV(0x23),
		MOD(0x24),
		NEG(0x25),
		INV(0x26),
		
		BITLS(0x27),
		BITRS(0x28),
		BITAND(0x29),
		BITOR(0x2A),
		BITXOR(0x2B),
		BITNEG(0x2C),
		
		POW(0x2D),
		
		CMPEQ(0x30),
		CMPGT(0x31),
		CMPLT(0x32),
		CMPGTE(0x33),
		CMPLTE(0x34),
		CMPNEQ(0x35),
		
		J(0x40),
		JT(0x41),
		JF(0x42),
		EXIT(0x43),
		RET(0x44),
		CALL(0x45),
		CALLEXT(0x46),
		
		CHOICEBEG(0x47),
		
		CHOICEADD(0x48),
		CHOICEADDT(0x49),
		CHOICESEL(0x4A),
		
		CHOOSEADD(0x4B),
		CHOOSEADDT(0x4C),
		CHOOSESEL(0x4D),
		
		TEXTRUN(0x4E),
		
		PATCH_CALL(0xFF); // Unused?
		
		private final int value;
		
		private Opcode(int value) {
			this.value = value;
		}
		
		public static Opcode from(byte value) {
			for(Opcode opcode : values()) {
				if(opcode.value == value) {
					return opcode;
				}
			}
			throw new IllegalArgumentException(String.format("Invalid opcode: 0x%x", value));
		}
	}
	
	private static final Set<Opcode> STRING_RESOLVE = EnumSet.of(
			Opcode.PUSHS,
			Opcode.PUSHBS,
			Opcode.CALLEXT,
			Opcode.PUSHINTS,
			Opcode.PUSHBINTS,
			Opcode.PUSHVARGLB,
			Opcode.SETVARGLB
	);

	private Type type;
	private Opcode opcode;
	private int arg1, arg2;
	private double argDouble;
	
	public DNXBytecode(ByteBuffer reader) {
		opcode = Opcode.from(reader.get());
		determineType();
		switch(type) {
		case SINGLE:
			arg1 = reader.getInt();
			break;
		case DOUBLE:
			arg1 = reader.getInt();
			arg2 = reader.getInt();
			break;
		case FLOAT:
			argDouble = reader.getDouble();
			break;
		default: break;
		}
	}
	
	public DNXBytecode(DNXFile reader, Opcode opcode, Object... args) {
		this.opcode = opcode;
		determineType();
		
		switch(type) {
		case SINGLE:
			if(STRING_RESOLVE.contains(opcode)) {
				arg1 = parseArgString(reader, args[0]);
			}
			else {
				arg1 = parseArgInt(args[0]);
			}
			break;
		
		case DOUBLE:
			int index;
			if(opcode == Opcode.CALL) {
				if(args[0] instanceof DNXFunction) {
					index = reader.getFunctions().indexOf(args[0]);
					if(index == -1) {
						index = reader.getFunctions().size();
						reader.addFunction((DNXFunction)args[0]);
					}
				}
				else {
					String name = String.valueOf(args[0]);
					DNXFunction func = reader.functionByName(name);
					if(func == null) {
						throw new IllegalArgumentException("Unknown function '" + name + "' - did you mean to use the CALLEXT opcode instead?");
					}
					index = reader.getFunctions().indexOf(func);
				}
				arg1 = index;
			}
			else if(opcode == Opcode.CALLEXT) {
				arg1 = parseArgString(reader, args[0]);
			}
			else {
				arg1 = parseArgInt(args[0]);
			}
			arg2 = parseArgInt(args[1]);
			break;
		case FLOAT:
			argDouble = parseArgDouble(args[0]);
			break;
		default: break;
		}
	}
	
	private int parseArgString(DNXFile reader, Object arg) {
		DNXString string;
		int index;
		if(arg instanceof DNXString) {
			string = (DNXString)arg;
			if(opcode == Opcode.PUSHS || opcode == Opcode.PUSHINTS) {
				index = reader.getTranslationStrings().indexOf(string);
				if(index == -1) {
					index = reader.getTranslationStrings().size();
					reader.addTranslationString(string);
				}
			}
			else {
				index = reader.getStrings().indexOf(string);
				if(index == -1) {
					index = reader.getStrings().size();
					reader.addString(string);
				}
			}
			return index;
		}
		else {
			String val = String.valueOf(arg);
			if(opcode == Opcode.PUSHS || opcode == Opcode.PUSHINTS) {
				string = reader.newTranslationString(val);
				return reader.getTranslationStrings().indexOf(string);
			}
			else {
				string = reader.newString(val);
				return reader.getStrings().indexOf(string);
			}
		}
	}
	
	private int parseArgInt(Object arg) {
		try {
			return Integer.parseInt(String.valueOf(arg));
		}
		catch(Exception e) {
			throw new IllegalArgumentException("Expected integer argument for opcode " + opcode + ", got " + arg + " (" + (arg == null ? null : arg.getClass().getName() + ")"));
		}
	}
	
	private double parseArgDouble(Object arg) {
		try {
			return Double.parseDouble(String.valueOf(arg));
		}
		catch(Exception e) {
			throw new IllegalArgumentException("Expected floating-point argument for opcode " + opcode + ", got " + arg + " (" + (arg == null ? null : arg.getClass().getName() + ")"));
		}
	}
	
	private void determineType() {
		switch(opcode) {
		case FREELOC:
		case PUSHI:
		case PUSHS:
		case PUSHBS:
		case SETVARGLB:
		case SETVARLOC:
		case PUSHVARGLB:
		case PUSHVARLOC:
		case J:
		case JT:
		case JF:
		case CHOICEADD:
		case CHOICEADDT:
		case CHOOSEADD:
		case CHOOSEADDT:
		case MAKEARR:
			type = Type.SINGLE;
			break;
		case CALL:
		case CALLEXT:
		case PUSHINTS:
		case PUSHBINTS:
			type = Type.DOUBLE;
			break;
		case PUSHD:
			type = Type.FLOAT;
			break;
		default: type = Type.DEFAULT;
		}
	}
	
	@Override
	public void serialize(DNXFile reader, LittleEndianDataOutputStream buff) throws IOException {
		buff.write(opcode.value);
		switch(type) {
		case DOUBLE:
			buff.writeInt(arg1);
			buff.writeInt(arg2);
			break;
		case FLOAT:
			buff.writeDouble(argDouble);
			break;
		case SINGLE:
			buff.writeInt(arg1);
			break;
		default:
			break;
		}
	}
	
	
	public double getDoubleArg() {
		return argDouble;
	}
	
	public Opcode getOpcode() {
		return opcode;
	}
	
	public int getFirstArg() {
		return arg1;
	}
	
	public int getSecondArg() {
		return arg2;
	}
	
	public Type getType() {
		return type;
	}
	
	public String toString() {
		return toString(null);
	}
	public String toString(DNXFile reader) {
		StringBuilder sb = new StringBuilder(opcode.name().toLowerCase());
		sb.append(" ");
		switch(type) {
		case SINGLE: 
			sb.append(parseFirst(reader));
			break;
		case DOUBLE: sb.append(parseFirst(reader)); sb.append(" "); sb.append(arg2); break;
		case FLOAT: sb.append(argDouble); break;
		default: break;
		}
		return sb.toString();
	}
	
	public String parseFirst(DNXFile reader) {
		return parseFirst(reader, true);
	}
	
	public String parseFirst(DNXFile reader, boolean quote) {
		if(reader == null) {
			return String.valueOf(arg1);
		}
		String str = "<unknown string>";
		if(STRING_RESOLVE.contains(opcode)) {
			str = (opcode == Opcode.PUSHS || opcode == Opcode.PUSHINTS ? reader.getTranslationStrings() : reader.getStrings()).get(arg1).getClean();
		}
		else if(opcode == Opcode.CALL) {
			str = reader.getFunctions().get(arg1).name.getClean();
		}
		else {
			return String.valueOf(arg1);
		}
		if(quote && opcode.name().endsWith("S")) {
			return '"' + str + '"';
		}
		return str;
	}
}
