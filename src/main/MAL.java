package main;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import mangaLib.MangaInfo;
import mangaLib.Poster;
import mangaLib.scrapers.MangaFox;
import visionCore.dataStructures.tuples.Tuple;
import visionCore.util.Files;
import visionCore.util.Web;

public class MAL extends mangaLib.MAL {

	
	public static HashMap<Integer, Tuple<String, String>> mappings;
	
	
	public static void load() {
		
		mappings = new HashMap<Integer, Tuple<String, String>>();
		
		File xml = new File(Main.abspath+"/malMappings.xml");
		if (xml.exists()) {
			
			SAXReader reader = new SAXReader();
			Document doc = null;
			try { doc = reader.read(xml); }
			catch (DocumentException e) { System.out.println("Failed at reading \""+xml.getAbsolutePath().replace("\\", "/")+"\""); return; }
			
			Element root = doc.getRootElement();
			
			for (Iterator<Element> it = root.elementIterator(); it.hasNext();) {
				Element elem = it.next();
				String elemName = elem.getName().toLowerCase().trim();
				
				if (elemName.equals("mapping")) {
					
					int id = -1;
					String url = null;
					String title = null;
					
					for (Iterator<Attribute> i = elem.attributeIterator(); i.hasNext();) {
						Attribute att = i.next();
						
						if (att.getName().toLowerCase().replace("_", "").equals("malid")) {
							
							try { id = Integer.parseInt(att.getText().trim()); } catch (Exception e) { e.printStackTrace(); }
							
						} else if (att.getName().toLowerCase().replace("_", "").equals("mangafoxurl")) {
							
							url = att.getText().trim();
							
						} else if (att.getName().toLowerCase().equals("title")) {
							
							title = att.getText().trim();
							
						}
					}
					
					if (id > -1 && url != null && url.length() > 5 && title != null && title.length() > 0) {
						
						mappings.put(id, new Tuple<String, String>(title, url));
					}
					
				}
				
			}
			
		}
		
	}
	
	
	public static void dumpList(File outdir, String username) {
		
		dumpList(outdir, username, -1);
	}
	
	public static void dumpList(File outdir, String username, int statusFilter) {
		
		dumpList(getMangaList(username, statusFilter), outdir, username);
	}
	
	public static void dumpList(List<MALEntry> list, File outdir, String username) {
		if (list == null || list.isEmpty()) { return; }
		
		if (outdir.exists() && outdir.listFiles() != null && outdir.getName().startsWith("mal_")) {
			
			//Files.cleanseDir(outdir);
			
			for (File f : outdir.listFiles()) {
				if (!f.isDirectory()) { continue; }
				
				File l = new File(f.getAbsolutePath()+"/_metadata/notdone");
				if (l.exists()) {
					
					Files.cleanseDir(f);
					f.delete();
				}
			}
		}
		
		System.out.println("Dumping metadata for "+username+"'s manga-list.\n");
		
		for (MALEntry entry : list) {
			
			Tuple<String, String> mapping = mappings.get(entry.id);
			if (mapping != null) {
				
				entry.title = mapping.x;
			}
			
			File dir = new File(outdir.getAbsolutePath()+"/"+entry.title+"/_metadata");
			if (dir.exists()) { continue; }
			dir.mkdirs();
			
			File posterdir = new File(dir.getAbsolutePath()+"/posters");
			posterdir.mkdirs();
			
			MangaInfo info = new MangaInfo();
			
			if (mapping != null) {
				
				info.title = mapping.x;
				info.url = mapping.y;
			}
			
			info.title = entry.title;
			
			info.save(new File(dir.getAbsolutePath()+"/info.xml"));
			
			File notdone = new File(dir.getAbsolutePath()+"/notdone");
			if (!notdone.exists()) { try { notdone.createNewFile(); } catch (Exception e) {} }
			
			File notdoneP = new File(posterdir.getAbsolutePath()+"/notdone");
			if (!notdoneP.exists()) { try { notdoneP.createNewFile(); } catch (Exception e) {} }
			
		}
		
		
		System.out.println("Loading posters.. almost done.");
		
		
		ExecutorService xctr = Executors.newCachedThreadPool();
		
		try {
		
			for (int j = 0, size = list.size(); j < size; j++) {
				MALEntry entry = list.get(j);
				
				xctr.submit(new Runnable(){
					
					@Override
					public void run() {
						
						for (int k = 0; k < 100; k++) {
							
							try {
								
								BufferedImage img = Web.getImage(entry.posterUrl);
								
								File pfile = new File(outdir.getAbsolutePath()+"/"+entry.title+"/_metadata/posters/01.jpg");
								
								if (!pfile.exists()) {
									
									Poster.saveResized(img, pfile, Poster.MAL_WIDTH, Poster.MAL_HEIGHT);
								}
								
								File notdoneP = new File(outdir.getAbsolutePath()+"/"+entry.title+"/_metadata/posters/notdone");
								if (notdoneP.exists()) { try { notdoneP.delete(); } catch (Exception e) {} }
								
								break;
								
							} catch (Exception | Error e) { }
						}
						
					}
					
				});
				
				xctr.submit(new Runnable(){
					
					@Override
					public void run() {
					
						File dir = new File(outdir.getAbsolutePath()+"/"+entry.title+"/_metadata");
						if (!dir.exists()) { return; }
						
						File notdone = new File(dir.getAbsolutePath()+"/notdone");
						if (!notdone.exists()) { return; }
						
						File file = new File(dir.getAbsolutePath()+"/info.xml");
						if (!file.exists()) { return; }
						
						MangaInfo info = null;
						try { info = MangaInfo.load(file); }
						catch (Exception | Error e) { return; }
						
						String url = info.url;
						if (url == null || url.length() < 5) {
							
							url = new MangaFox().searchManga(entry.title).get(0).url;
						}
						
						info = new MangaFox().getInfo(url, null, info);
						
						info.save(file);
						
						if (notdone.exists()) { notdone.delete(); }
						
					}
					
				});
				
			}
		} finally { xctr.shutdown(); }
		
		try {
			
			xctr.awaitTermination(300, TimeUnit.MINUTES);
			
		} catch (Exception | Error e) { e.printStackTrace(); }
		
		
		System.out.println("All done.");
		
	}
	
	
	public static void downloadPosters(int id, String relPosterDir, int namingOffset) {
		
		String url = "https://myanimelist.net/manga/"+id;
		String html = Web.getHTML(url, false);
		
		for (int i = 0; i < 100 && (html == null || html.length() < 100); i++) {
			
			html = Web.getHTML(url, false);
		}
		if (html == null) { return; }
		
		html = html.substring(0, html.indexOf("/pics\">"));
		
		String f = "href=";
		html = html.substring(html.lastIndexOf(f)+f.length()+1);
		
		String mangaUrl = Web.clean(html);
		
		downloadPostersByUrl(mangaUrl, relPosterDir, namingOffset);
	}
	
	public static void downloadPosters(String title, String relPosterDir, int namingOffset) {
		
		String url = MANGA_SEARCH_URL + title.toLowerCase().trim().replace(" ", SPACE_REPLACE);
		
		String html = null;
		
		for (int i = 0; i < 100 && (html == null || html.length() < 100); i++) {
			
			html = Web.getHTML(url, false);
		}
		
		String mangaUrl = getMangaUrlFromSearch(html);
		
		downloadPostersByUrl(mangaUrl, relPosterDir, namingOffset);
	}
	
	public static void downloadPostersByUrl(String malUrl, String relPosterDir, int namingOffset) {
		
		if (malUrl.endsWith("/")) { malUrl = malUrl.substring(0, malUrl.length()-1); }
		
		String html = Web.getHTML(malUrl+"/pics", false);
		
		String f = "<div class=\"wrapper\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<div id=\"content\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<div id=\"horiznav_nav";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = ">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "</div>";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<div class=\"wrapper\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<h2 class=\"mb8\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<div class=\"wrapper\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<table border";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = ">";
		html = html.substring(html.indexOf(f)+f.length());
		
		int idx = html.indexOf("</table>");
		if (idx == -1) { idx = html.indexOf("<div class=\"ar mt8\">"); }
		
		String tableHTML = html.substring(0, idx);
		
		List<String> picUrls = new ArrayList<String>();
		
		for (int i = 0; i < 1000 && (tableHTML.contains("<td>") || tableHTML.contains("<td ")) && tableHTML.contains("</td>"); i++) {
			
			f = "<td>";
			tableHTML = tableHTML.substring(tableHTML.indexOf(f)+f.length());
			
			f = "<a href=";
			tableHTML = tableHTML.substring(tableHTML.indexOf(f)+f.length()+1);
			
			String picurl = tableHTML.substring(0, tableHTML.indexOf(".jpg")+4);
			picUrls.add(picurl.trim());
			
			f = "</div>";
			tableHTML = tableHTML.substring(tableHTML.indexOf(f)+f.length());
			
			f = "</td>";
			tableHTML = tableHTML.substring(tableHTML.indexOf(f)+f.length());
			
		}
		
		ExecutorService exec = Executors.newCachedThreadPool();
		
		try {
		
			for (int i = 0; i < picUrls.size(); i++) {
			
				final String picurl = picUrls.get(i);
				final int imgnr = i + 1 + namingOffset;
				
				exec.submit(new Runnable(){
	
					@Override
					public void run() {
						
						String imgname = imgnr+"";
						for (int j = 0, l = imgname.length(); j < 2 - l; j++) { imgname = "0"+imgname; }
						
						for (int j = 0; j < 1000; j++) {
						
							try {
								
								BufferedImage img = Web.getImage(picurl);
								
								for (String metaout : Main.metaOuts) {
								
									File out = new File(metaout+"/"+relPosterDir+"/"+imgname+".jpg");
									
									if (out.exists()) { continue; }
									else { out.getParentFile().mkdirs(); }
									
									if (img != null && (img.getWidth() <= 1 || img.getHeight() <= 1)) { continue; }
									
									Poster.saveResized(img, out, Poster.MAL_WIDTH, Poster.MAL_HEIGHT);
								}
								
								break;
								
							} catch (Exception | Error e) {}
							
							try { Thread.sleep(500); } catch (Exception | Error e) {}
							
						}
						
					}
					
				});
				
			}
			
		} finally {
			
			exec.shutdown();
		}
		
		try { exec.awaitTermination(5, TimeUnit.MINUTES); } catch (Exception | Error e) { e.printStackTrace(); }
	}
	
}
