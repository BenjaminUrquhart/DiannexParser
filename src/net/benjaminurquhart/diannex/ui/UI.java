package net.benjaminurquhart.diannex.ui;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileFilter;

import net.benjaminurquhart.diannex.DNXAssembler;
import net.benjaminurquhart.diannex.DNXBytecode;
import net.benjaminurquhart.diannex.DNXCompiled;
import net.benjaminurquhart.diannex.DNXDefinition;
import net.benjaminurquhart.diannex.DNXFile;
import net.benjaminurquhart.diannex.DNXFunction;
import net.benjaminurquhart.diannex.DNXScene;
import net.benjaminurquhart.diannex.DNXString;
import net.benjaminurquhart.diannex.GenericWorker;

public class UI extends JPanel implements ActionListener {
	
	private static final long serialVersionUID = -8751465600892495447L;
	private static final Object lock = new Object();
	
	public JTextArea infoText;
	public JProgressBar progressBar;
	public JButton importButton, exportButton, saveButton;
	
	private File file;
	private DNXFile data;
	private boolean ready;
	
	private final JFileChooser fileSelector = new JFileChooser(), folderSelector = new JFileChooser();
	
	private static volatile UI instance;
	
	public static UI getInstance() {
		if(instance == null) {
			synchronized(lock) {
				if(instance == null) {
					instance = new UI();
				}
			}
		}
		return instance;
	}
	
	private UI() {
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("");
		progressBar.setValue(0);
		
		importButton = new JButton("Import");
		importButton.setActionCommand("import");
		importButton.addActionListener(this);
		
		exportButton = new JButton("Export");
		exportButton.setActionCommand("export");
		exportButton.addActionListener(this);
		
		saveButton = new JButton("Save");
		saveButton.setActionCommand("save");
		saveButton.addActionListener(this);
		
		fileSelector.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileSelector.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return true;
			}

			@Override
			public String getDescription() {
				return "Diannex Binary (.dxb)";
			}
		});
		
		folderSelector.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		folderSelector.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return true;
			}

			@Override
			public String getDescription() {
				return "";
			}
		});
		
		while(file == null || !file.exists() || file.isDirectory()) {
			if(fileSelector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				file = fileSelector.getSelectedFile();
			}
			else {
				System.exit(0);
				return;
			}
		}
		infoText = new JTextArea("No file loaded");
		infoText.setEditable(false);
		
		setLayout(new BorderLayout());
		
		// TODO: add a button to open a dnx file.
		// I just copied this from another project on short notice 
		// and can't be bothered to redo layout stuff.
		add(infoText, BorderLayout.PAGE_START);
		add(importButton, BorderLayout.LINE_START);
		add(saveButton, BorderLayout.CENTER);
		add(exportButton, BorderLayout.LINE_END);
		add(progressBar, BorderLayout.PAGE_END);
		
		ready = true;
		GenericWorker worker = new GenericWorker("Loading...", () -> {
			try {
				disableControls();
				data = new DNXFile(file);
				updateInfoText();
				onFinish(null);
			}
			catch(Exception e) {
				onFinish(e);
				System.exit(1);
			}
		});
		worker.execute();
	}
	
	public void updateInfoText() {
		if(data == null) {
			infoText.setText("No file loaded");
		}
		else {
			infoText.setText(String.format("Scenes: %d\nFunctions: %d\nDefinitions: %d", data.getScenes().size(), data.getFunctions().size(), data.getDefinitions().size()));
		}
		Main.frame.pack();
	}

	@Override
	public void actionPerformed(ActionEvent event) {
		GenericWorker worker = null;
		disableControls();
		switch(event.getActionCommand()) {
		case "import": {
			folderSelector.setCurrentDirectory(file.getParentFile());
			if(folderSelector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				worker = new GenericWorker("Importing...", () -> {
					try {
						String name, text;
						DNXCompiled entry;
						boolean defining = false, isScene, isFunc, isDef;
						Set<File> files = crawlFolder(folderSelector.getSelectedFile());
						List<DNXBytecode> bytecode = null;
						for(File file : files) {
							isScene = file.getParentFile().getName().equals("scenes");
							isFunc = file.getParentFile().getName().equals("functions");
							isDef = file.getParentFile().getName().equals("definitions");
							if(!isScene && !isFunc && !isDef) {
								System.out.printf("Ignoring %s\n", file.getAbsolutePath());
								continue;
							}
							text = Files.readString(file.toPath());
							if(isDef && file.getName().endsWith(".def")) {
								defining = true;
							}
							else if(file.getName().endsWith(".asm")){
								bytecode = DNXAssembler.assemble(text, data);
								defining = false;
							}
							else {
								System.out.printf("Ignoring %s\n", file.getAbsolutePath());
								continue;
							}
							name = file.getName().substring(0, file.getName().length() - 4);
							progressBar.setString(name);
							if(isScene) {
								entry = data.sceneByName(name);
								if(entry == null) {
									entry = new DNXScene(data.newString(name));
									data.addScene((DNXScene)entry);
								}
							}
							else if(isFunc) {
								entry = data.functionByName(name);
								if(entry == null) {
									entry = new DNXFunction(data.newString(name), null);
									data.addFunction((DNXFunction)entry);
								}
							}
							else if(isDef) {
								entry = data.definitionByName(name);
								if(entry == null) {
									entry = new DNXDefinition(data.newString(name), data.newString(""));
									data.addDefinition((DNXDefinition)entry);
								}
							}
							else {
								// Impossible, here to stop the compiler from complaining
								continue;
							}
							if(defining) {
								DNXString def = null;
								for(DNXString s : data.getTranslationStrings()) {
									if(s.get().equals(text)) {
										def = s;
										break;
									}
								}
								if(def == null) {
									for(DNXString s : data.getStrings()) {
										if(s.get().equals(text)) {
											def = s;
											break;
										}
									}
								}
								if(def == null) {
									def = data.newString(text);
								}
								((DNXDefinition)entry).reference = def;
							}
							else {
								entry.instructions = bytecode;
							}
						}
						updateInfoText();
					}
					catch(Exception e) {
						throwUnchecked(e);
					}
				});
			}
			else {
				onFinish(null);
			}
		} break;
		case "export": {
			folderSelector.setCurrentDirectory(file.getParentFile());
			if(folderSelector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				worker = new GenericWorker("Exporting...", () -> {
					try {
						dump(data);
					}
					catch(Exception e) {
						throwUnchecked(e);
					}
				});
			}
		} break;
		case "save": {
			fileSelector.setCurrentDirectory(file.getParentFile());
			if(fileSelector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				worker = new GenericWorker("Saving...", () -> {
					try {
						data.write(file);
					}
					catch(Exception e) {
						throwUnchecked(e);
					}
				});
			}

		} break;
		}
		if(worker != null) {
			worker.execute();
		}
		else {
			enableControls();
		}
	}
	
	private void disableControls() {
		if(ready) {
			importButton.setEnabled(false);
			exportButton.setEnabled(false);
			saveButton.setEnabled(false);
			
			Main.frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		}
	}
	
	private void enableControls() {
		if(ready) {
			importButton.setEnabled(true);
			exportButton.setEnabled(true);
			saveButton.setEnabled(true);
			
			Main.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}
	}
	
	public void onFinish(Throwable e) {
		Main.frame.pack();
		if(e != null) {
			e.printStackTrace(System.out);
			Toolkit.getDefaultToolkit().beep();
			progressBar.setString("Internal error");
			JOptionPane.showMessageDialog(
					null, 
					"An internal error has occured:\n" 
					+ e.toString() 
					+ "\n" + Arrays.stream(e.getStackTrace()).map(String::valueOf).collect(Collectors.joining("\n\t")), 
					"Internal error", 
					JOptionPane.ERROR_MESSAGE
			);
		}
		else {
			progressBar.setString("");
		}
		enableControls();
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
	}
	
	private static void dump(DNXFile file) throws IOException {
		File root = new File("output");
		disassembleAll(file, file.getScenes(), root, "scenes");
		disassembleAll(file, file.getFunctions(), root, "functions");
		disassembleAll(file, file.getDefinitions(), root, "definitions");
		
		File defFolder = new File(root, "definitions");
		for(DNXDefinition def : file.getDefinitions()) {
			Files.write(new File(defFolder, def.name.get() + ".def").toPath(), def.reference.get().getBytes());
		}
	}
	
	private static void disassembleAll(DNXFile file, List<? extends DNXCompiled> list, File root, String folderName) throws IOException {
		File folder = new File(root, folderName);
		
		if(!folder.exists()) {
			folder.mkdirs();
		}
		
		for(DNXCompiled entry : list) {
			Files.write(new File(folder, entry.name.get() + ".asm").toPath(), entry.disassemble(file).getBytes());
		}
	}
	
	private static Set<File> crawlFolder(File folder) {
		Set<File> found = new HashSet<>();
		crawlFolder(folder, found);
		return found;
	}
	
	private static void crawlFolder(File folder, Set<File> found) {
		if(!folder.isDirectory()) {
			found.add(folder);
			return;
		}
		for(File file : folder.listFiles()) {
			if(file.isDirectory()) {
				crawlFolder(file, found);
			}
			else {
				found.add(file);
			}
		}
	}
	
	// I love Java
    @SuppressWarnings("unchecked")
    private static <E extends Exception> void throwUnchecked(Exception e) throws E {
        throw (E) e;
    }
}
