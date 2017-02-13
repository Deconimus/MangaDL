package main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import mangaLib.MAL.MALEntry;
import mangaLib.MangaInfo;
import visionCore.io.MultiPrintStream;
import visionCore.util.Files;

public class Main {

	public static final int MODE_SEARCH = 0, MODE_DOWNLOAD = 1, MODE_URL_DOWNLOAD = 2, MODE_UPDATE = 3, MODE_REFRESH = 4, 
							MODE_DUMP_SEARCH = 5, MODE_DUMP_MAL = 6, MODE_DOWNLOAD_CHAPTER = 7;
	
	public static String title, mangapath;
	public static boolean noUserInput = false, chsubs = true;
	public static int mode = -1;
	
	public static List<String> metaOuts;
	
	public static String abspath;
	
	
	public static void main(String[] args) {
		
		try {
			
			abspath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath().replace("\\", "/");
			
			if (abspath.endsWith("/bin")) {
				
				abspath = abspath.substring(0, abspath.indexOf("/bin"));
			}
			
			if (abspath.endsWith(".jar")) {
				
				abspath = new File(abspath).getParentFile().getAbsolutePath();
			}
			
		} catch (Exception e) { e.printStackTrace(); }
		
		setOutStream(false);
		
		metaOuts = new ArrayList<String>();
		
		loadConfig();
		
		MAL.load();
		
		if (!parseArguments(args)) { return; }
		
		metaOuts = metaOuts.stream().filter(s -> !s.trim().equals(mangapath.trim())).collect(Collectors.toList());
		metaOuts.add(mangapath.trim());
		
		File mangadir = new File(mangapath);
		if (!mangadir.exists()) { mangadir.mkdirs(); }
		
		System.out.println();
		
		if (mode == MODE_SEARCH) {
			
			Result[] results = MangaFox.search(title);
			
			if (results == null || results.length == 0) {
				
				System.out.println("No manga named+\""+title+"\" found.");
				return;
			}
			
			System.out.println("Which manga would you like to download?\n");
			
			for (int i = 0; i < results.length; i++) {
				Result r = results[i];
				
				System.out.println("["+i+"] "+r.title);
				
			}
			
			System.out.println();
			System.out.println("Enter a number or x to exit.");
			
			int nr = -1;
			
			while (true) {
				
				String in = System.console().readLine().trim().toLowerCase();
				
				if (in.contains("x") || in.equals("close") || in.equals("quit")) {
					
					return;
				}
				
				nr = -1;
				
				try {
					
					nr = Integer.parseInt(in);
					
				} catch (Exception e) {}
				
				if (nr >= 0 && nr < results.length) {
					
					break;
				}
				
				System.out.println("Please enter a valid input.");
				
			}
			
			System.out.println();
			
			MangaFox.download(null, results[nr].url, mangadir, chsubs);
			
		} else if (mode == MODE_DOWNLOAD) {
			
			Result result = null;
			
			MangaInfo info = searchInFolder(title);
			
			if (info == null) {
			
				Result[] results = MangaFox.search(title);
				
				if (results == null || results.length == 0) {
					
					System.out.println("No manga named+\""+title+"\" found.");
					return;
				}
				
				if (!noUserInput) {
				
					System.out.println("Download \""+results[0].title+"\" ? [Y/n]");
					
					String in = System.console().readLine().trim().toLowerCase();
					
					if (!in.contains("y")) { return; }
					
					System.out.println();
				}
				
				result = results[0];
			}
			
			String url = (info != null) ? info.url : result.url;
			
			download(info, url, mangadir);
			
		} else if (mode == MODE_URL_DOWNLOAD) {
			
			download(null, title, mangadir);
			
		} else if (mode == MODE_UPDATE) {
			
			updateMangaDir(mangadir);
			
		} else if (mode == MODE_REFRESH) {
			
			if (title == null || title.trim().length() <= 0) {
				
				refreshMangaDir(mangadir);
				
			} else {
				
				refreshManga(mangadir, title);
			}
			
		} else if (mode == MODE_DUMP_SEARCH) {
			
			if (title == null || title.trim().toLowerCase().equals("hot")) {
				
				dumpHot();
				
			} else {
				
				dumpSearch(title);
				
			}
			
		} else if (mode == MODE_DUMP_MAL) {
			
			dumpMAL(title);
			
		} else if (mode == MODE_DOWNLOAD_CHAPTER) {
			
			if (title.contains("mangafox.me")) {
				
				String chapNr = title;
				
				if (title.endsWith(".html")) {
					
					chapNr = chapNr.substring(0, chapNr.lastIndexOf("/"));
					chapNr = chapNr.substring(chapNr.lastIndexOf("/c")+2).trim();
				}
				
				File chapdir = new File(abspath+"/downloaded/"+chapNr);
				if (chapdir.exists()) { Files.cleanseDir(chapdir); chapdir.delete(); }
				chapdir.mkdirs();
				
				MangaFox.saveChapter("", "", title, chapdir);
				
			} else if (title.contains("mangaseeonline.net")) {
				
				if (title.contains("-page-")) {
					
					title = title.substring(0, title.lastIndexOf("-page-"))+".html";
				}
				
				String chapNr = title.substring(title.lastIndexOf("-")+1, title.lastIndexOf(".html")).trim();
				
				File chapdir = new File(abspath+"/downloaded/"+chapNr);
				if (chapdir.exists()) { Files.cleanseDir(chapdir); chapdir.delete(); }
				chapdir.mkdirs();
				
				MangaSeeOnline.saveChapter(title, chapdir);
				
			} else {
				
				System.out.println("URL corrupt or mangasite not supported.");
			}
		}
		
		//MangaFox.download(MangaFox.findUrl("bleach"), new File("H:/Mangas/"));
		
	}
	
	private static void download(MangaInfo info, String url, File mangadir) {
		
		MangaFox.download(info, url, mangadir, chsubs);
	}
	
	private static void updateMangaDir(File mangadir) {
		
		System.out.println("Updating whole mangadir... hold up\n");
		
		List<File> files = Files.getFiles(new File(mangapath), f -> f.isDirectory() && !f.getName().startsWith("_"));
		if (files.isEmpty()) { return; }
		
		for (File f : files) {
			
			File metadir = new File(f.getAbsolutePath()+"/_metadata");
			if (!metadir.exists()) { continue; }
			
			File infoF = new File(metadir.getAbsolutePath().replace("\\", "/")+"/info.xml");
			if (!infoF.exists()) { continue; }
			
			File dlCompleteFlag = new File(metadir.getAbsolutePath()+"/DL_COMPLETE");
			if (dlCompleteFlag.exists()) { System.out.println("Skipping \""+f.getName()+"\"\n"); continue; }
			
			MangaInfo info = MangaInfo.load(infoF, true);
			if (info == null || info.url == null || info.url.trim().length() < 5) { continue; }
			
			download(info, info.url, mangadir);
			System.out.println();
			
		}
		
	}
	
	private static void refreshMangaDir(File mangadir) {
		
		System.out.println("Refreshing manga metadata.\n");
		
		List<File> files = Files.getFiles(mangadir, f -> f.isDirectory() && !f.getName().startsWith("_"));
		
		for (File f : files) {
			
			refreshManga(mangadir, f);
		}
		
	}
	
	private static void refreshManga(File mangadir, String title) {
		
		List<File> files = Files.getFiles(new File(mangapath), f -> f.isDirectory() && !f.getName().startsWith("_"));
		if (files.isEmpty()) { return; }
		
		MangaFox.sortResults(files, title);
		
		File dir = files.get(0);
		
		dir = new File(dir.getAbsolutePath().replace("\\", "/"));
		if (!dir.exists()) { System.out.println("Couldn't find \""+title+"\""); return; }
		
		refreshManga(mangadir, dir);
		
	}
	
	private static void refreshManga(File mangadir, File dir) {
		
		File infoFile = new File(dir.getAbsolutePath().replace("\\", "/")+"/_metadata/info.xml");
		if (!infoFile.exists()) { return; }
		
		mangaLib.MangaInfo info = mangaLib.MangaInfo.load(infoFile);
		if (info == null) { System.out.println("Couldn't find \"old\" metadata."); return; }
		
		if (info.url == null || info.url.trim().length() < 5) { return; }
		
		System.out.println("Refreshing \""+info.title+"\"s metadata.");
		
		for (String metaout : metaOuts) {
			
			File mangaPosters = new File(metaout+"/"+info.title.trim()+"/_metadata/posters");
			
			if (mangaPosters.exists()) {
			
				for (File f : mangaPosters.listFiles()) {
					if (!f.isFile() || !ImageFormats.isSupported(f)) { continue; }
					
					String name = f.getName().substring(f.getName().lastIndexOf("."));
					
					if (name.length() == 2 && Character.isDigit(name.charAt(0))) {
						
						f.delete();
					}
					
				}
				
			}
			
		}
		
		MangaFox.parseInfo(info.url, mangadir);
		
	}
	
	private static void dumpHot() {
		
		MangaFox.dumpHot(new File(Main.abspath+"/tmp/hot"));
		
	}
	
	private static void dumpSearch(String title) {
		
		MangaFox.dumpSearch(title, new File(Main.abspath+"/tmp/"));
		
	}
	
	private static void dumpMAL(String username) {
		
		List<MALEntry> list = MAL.getMangaList(username);
		
		List<MALEntry> p2r = new ArrayList<MALEntry>();
		List<MALEntry> onHold = new ArrayList<MALEntry>();
		List<MALEntry> dropped = new ArrayList<MALEntry>();
		
		for (MALEntry entry : list) {
			
			if (entry.status == MAL.STATUS_ON_HOLD) {
				
				onHold.add(entry);
				
			} else if (entry.status == MAL.STATUS_PLAN_TO_READ) {
				
				p2r.add(entry);
				
			} else if (entry.status == MAL.STATUS_DROPPED) {
				
				dropped.add(entry);
				
			}
			
		}
		
		MAL.dumpList(p2r, new File(Main.abspath+"/tmp/mal_planToRead"), username);
		MAL.dumpList(onHold, new File(Main.abspath+"/tmp/mal_onHold"), username);
		MAL.dumpList(dropped, new File(Main.abspath+"/tmp/mal_dropped"), username);
		
	}
	
	private static boolean parseArguments(String[] args) {
		
		if (args.length <= 0) { 
			
			System.out.println();
			System.out.println("Necessary arguments are missing.");
			System.out.println("Instead of promting you to use the -h argument, I'll just do that for you:");
			
			help();
			
			return false;
		}
		
		for (int i = 0; i < args.length; i++) {
			String arg = args[i].trim().toLowerCase();
			
			String nextArg = null;
			if (i < args.length-1) { nextArg = args[i+1].replace("\"", "").trim(); }
			
			if (arg.startsWith("-s") || arg.startsWith("--search")) {
				
				if (nextArg != null && !nextArg.startsWith("-")) {
					
					mode = MODE_SEARCH;
					title = nextArg;
				}
				
			} else if (arg.startsWith("-d") || arg.startsWith("--download")) {
				
				if (nextArg != null && !nextArg.startsWith("-")) {
					
					if (nextArg.startsWith("http") || nextArg.startsWith("www") || nextArg.startsWith("mangafox.me")) {
						
						mode = MODE_URL_DOWNLOAD;
						
					} else { mode = MODE_DOWNLOAD; }
					
					title = nextArg;
					
				}
				
			} else if (arg.startsWith("-u") || arg.startsWith("--update")) {
				
				mode = MODE_UPDATE;
				
			} else if (arg.startsWith("-r") || arg.startsWith("--refresh")) {
				
				mode = MODE_REFRESH;
				
				if (nextArg != null && nextArg.length() > 0 && !nextArg.startsWith("-")) {
					
					title = nextArg.replace("\"", "").trim();
				}
				
			} else if (arg.startsWith("-n") || arg.startsWith("--noinput")) {
				
				noUserInput = true;
				
			} else if (arg.startsWith("-o") || arg.startsWith("--output")) {
				
				if (nextArg != null && !nextArg.startsWith("-")) {
				
					mangapath = nextArg.replace("\\", "/").trim().replace("\"", "");
				}
				
			} else if (arg.startsWith("-m") || arg.startsWith("--metaout")) {
				
				if (nextArg != null && !nextArg.startsWith("-")) {
					
					metaOuts.add(nextArg.replace("\\", "/").replace("\"", "").trim());
				}
				
			} else if (arg.startsWith("-h") || arg.startsWith("--help")) {
				
				help();
				return false;
				
			} else if (arg.startsWith("--dumpsearch")) {
				
				mode = MODE_DUMP_SEARCH;
				
				if (nextArg != null && !nextArg.startsWith("-")) {
					
					title = nextArg;
				}
				
			} else if (arg.startsWith("--dumpmal")) {
				
				mode = MODE_DUMP_MAL;
				
				if (nextArg != null && !nextArg.startsWith("-")) {
					
					title = nextArg;
				}
				
			} else if (arg.startsWith("-c") || arg.startsWith("--chapter")) {
				
				mode = MODE_DOWNLOAD_CHAPTER;
				
				if (nextArg != null && !nextArg.startsWith("-")) {
					
					title = nextArg;
				}
				
			} else if (arg.startsWith("--nochsubs")) {
				
				chsubs = false;
			}
			
		}
		
		saveConfig();
		
		if (mode == -1) {
			
			System.out.println("Necessary arguments missing..");
			help();
			return false;
		}
		
		if (mangapath == null) {
			
			System.out.println("Output directory not specified and no cfg found.");
			return false;
		}
		
		return true;
	}
	
	public static void help() {
		
		System.out.println("  __  __                         _____  _      ");
		System.out.println(" |  \\/  |                       |  __ \\| |     ");
		System.out.println(" | \\  / | __ _ _ __   __ _  __ _| |  | | |     ");
		System.out.println(" | |\\/| |/ _` | '_ \\ / _` |/ _` | |  | | |     ");
		System.out.println(" | |  | | (_| | | | | (_| | (_| | |__| | |____ ");
		System.out.println(" |_|  |_|\\__,_|_| |_|\\__, |\\__,_|_____/|______|");
		System.out.println("                      __/ |                    ");
		System.out.println("                     |___/                     ");
		System.out.println();
		System.out.println("============PARAMETERS:============");
		System.out.println();
		System.out.println("-s [or --search] <title> search for manga\n");
		System.out.println("-d [or --download] <title or url> download or update manga\n");
		System.out.println("-c [or --chapter] <url> download a single chapter\n");
		System.out.println("-u [or --update] update manga-dir\n");
		System.out.println("-r [or --refresh] <dir or blank> refresh metadata (whole dir or specified manga)).\n");
		System.out.println("-n [or --noinput] program won't ask for permission to download manga\n");
		System.out.println("-m [or --metaout] <dir> specifies where metadata will be saved. Default is mangadir. Can be called mulitple times.\n");
		System.out.println("-o [or --output] <dir> specifies output dir (where your manga are), needed if no cfg-file exists\n");
		System.out.println();
		System.out.println("--dumpsearch <blank> dumps info for mangafox top50 manga into tmp-dir.");
		System.out.println("--dumpsearch <title> dumps info for search-results into tmp-dir.");
		System.out.println("--dumpmal <username> dumps plan-to-read list for MAL-user into tmp-dir.");
		System.out.println();
		System.out.println("===================================");
		System.out.println();
		
	}
	
	
	public static void loadConfig() {
		
		File xml = new File(Main.abspath+"/cfg.xml");
		if (xml.exists()) {
			
			SAXReader reader = new SAXReader();
			Document doc = null;
			try { doc = reader.read(xml); }
			catch (DocumentException e) { System.out.println("Failed at reading \""+xml.getAbsolutePath().replace("\\", "/")+"\""); return; }
			
			Element root = doc.getRootElement();
			
			for (Iterator<Element> it = root.elementIterator(); it.hasNext();) {
				Element elem = it.next();
				String elemName = elem.getName().trim().toLowerCase();
				
				if (elemName.equals("mangadir")) {
					
					mangapath = elem.getText().trim();
					
				} else if (elemName.equals("metaout") || elemName.equals("metadata") || elemName.equals("metadir")) {
					
					metaOuts.add(elem.getText().trim());
					
				}
				
			}
			
		}
		
	}
	
	public static void saveConfig() {
		
		File xml = new File(Main.abspath+"/cfg.xml");
		
		if (!xml.exists()) { xml.getParentFile().mkdirs(); }
		
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("root");
		
		
		root.addElement("mangadir").setText(mangapath.trim().replace("\\", "/"));
		
		for (String metaout : metaOuts) {
			
			root.addElement("metaout").setText(metaout.trim().replace("\\", "/"));
		}
		
		
		OutputFormat format = OutputFormat.createPrettyPrint();
		format.setIndent("\t");
		
		try {
		
			Files.waitOnFile(xml, 8);
			if (xml.exists()) { xml.delete(); }
			
			XMLWriter writer = new XMLWriter(new FileWriter(xml), format);
			writer.write(doc);
			writer.close();
			
		} catch (Exception e) { e.printStackTrace(); }
		
	}
	
	
	public static void setOutStream(boolean keepPrevLog) {
		
		try {
			
			File logfile = new File(abspath+"/log.txt");
			
			String prevLines = "";
			
			if (keepPrevLog && logfile.exists()) {
				
				try {
				
					BufferedReader br = new BufferedReader(new FileReader(logfile));
					
					int i = 0;
					for (String line = ""; (line = br.readLine()) != null; i++) {
						line = line.replace("\n", "").replace("\r", "").trim();
						
						prevLines += line+"\r\n";
					}
					
					br.close();
					
				} catch (Exception e) { e.printStackTrace(); }
				
			}
			
			PrintStream logOut = new PrintStream(new FileOutputStream(logfile));
			
			if (keepPrevLog && logfile.exists()) { logOut.print(prevLines); }
			
			PrintStream multiOut = new MultiPrintStream(logOut, System.out);
			PrintStream multiErr = new MultiPrintStream(logOut, System.err);
			
			System.setOut(multiOut);
			System.setErr(multiErr);
			
		} catch (Exception | Error e) { e.printStackTrace(); }
		
	}
	
	
	private static MangaInfo searchInFolder(String title) {
		
		List<File> files = Files.getFiles(new File(mangapath), f -> f.isDirectory() && !f.getName().startsWith("_") && f.getName().trim().equalsIgnoreCase(title.trim()));
		if (files.isEmpty()) { return null; }
		
		MangaFox.sortResults(files, title);
		
		File dir = files.get(0);
		
		dir = new File(dir.getAbsolutePath().replace("\\", "/")+"/_metadata");
		if (!dir.exists()) { return null; }
		
		File info = new File(dir.getAbsolutePath().replace("\\", "/")+"/info.xml");
		if (!info.exists()) { return null; }
		
		return MangaInfo.load(info, true);
	}
	
	private static Result getResultFromInfo(File info) {
		
		Files.waitOnFile(info, 4);
		
		SAXReader reader = new SAXReader();
		Document doc = null;
		try { doc = reader.read(info); }
		catch (DocumentException e) { System.out.println("Failed at reading \""+info.getAbsolutePath().replace("\\", "/")+"\""); return null; }
		
		Element root = doc.getRootElement();
		
		String url = null;
		String t = null;
		try { url = root.element("url").getText().trim(); } catch (Exception e) { return null; }
		try { t = root.element("title").getText().trim(); } catch (Exception e) { return null; }
		
		return new Result(t, url);
	}
	
	
}
