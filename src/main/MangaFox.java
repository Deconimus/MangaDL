package main;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.IncorrectnessListener;
import com.gargoylesoftware.htmlunit.InteractivePage;
import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.ScriptResult;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptErrorListener;
import com.gargoylesoftware.htmlunit.util.Cookie;

import mangaLib.MangaInfo;
import visionCore.dataStructures.tuples.Quad;
import visionCore.dataStructures.tuples.Triplet;
import visionCore.math.FastMath;
import visionCore.util.Files;
import visionCore.util.StringUtils;
import visionCore.util.Web;

import mangaLib.Poster;

public class MangaFox {
	
	public static String findUrl(String title) {
		
		Result[] rs = search(title);
		
		if (rs != null && rs.length > 0) {
			
			return rs[0].url;
		}
		
		return null;
	}
	
	public static Result[] search(String title) {
		
		title = title.trim();
		
		title = title.toLowerCase();
		
		String searchtitle = title.replace(" ", "+").replace("&", "").replace("?", "").replace("=", "").replace("!", "");
		
		String html = Web.getHTML(searchUrl.replace(replace, searchtitle), false);
		
		String table = html.substring(html.indexOf("<table id=\"listing\">")+20);
		table = table.substring(0, table.indexOf("</table>"));
		
		table = table.substring(table.indexOf("</tr>")+5);
		
		String[] words = title.split(" ");
		
		List<Result> results = new ArrayList<Result>();
		
		for (int i = 0; table.contains("<tr>") && table.contains("</tr>") && i < 1000; i++) {
			
			String td = table.substring(table.indexOf("<td>")+4, table.indexOf("</td>"));
			
			String url = td.substring(td.indexOf("<a href=")+9, td.indexOf(" class=")-1);
			String name = td.substring(td.indexOf(">")+1, td.indexOf("</a>"));
			
			url = url.trim();
			name = name.trim();
			
			if (StringUtils.containsNum(name.toLowerCase(), Math.max(FastMath.ceilInt((double)words.length / (double)2), 1), (String[])words)) {
				
				results.add(new Result(name, url));
			}
			
			table = table.substring(table.indexOf("</tr>")+5);
		}
		
		final String t = title;
		
		sortResults(results, t);
		
		return results.toArray(new Result[results.size()]);
	}
	
	
	private static BufferedImage getMangaPoster(String html) {
		
		String f = "<div id=\"series_info\"";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<div class=\"cover\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<img";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "src=";
		html = html.substring(html.indexOf(f)+f.length()+1);
		
		String u = html.substring(0, html.indexOf("\" onerror=")).trim();
		
		BufferedImage img = null;
		
		for (int i = 0; i < 100; i++) {
			
			img = Web.getImage(u);
			
			if (img != null) { break; }
		}
		
		return img;
	}
	
	
	private static MangaInfo getMangaInfo(String url) {
		
		return getMangaInfo(url, null, null);
	}
	
	public static MangaInfo getMangaInfo(String url, MangaInfo info) {
		
		return getMangaInfo(url, null, info);
	}
	
	private static MangaInfo getMangaInfo(String url, String html) {
		
		return getMangaInfo(url, html, null);
	}
	
	private static MangaInfo getMangaInfo(String url, String html, MangaInfo info) {
		
		if (html == null || html.trim().length() < 1) { html = Web.getHTML(url, false); }
		if (info == null) { info = new MangaInfo(); }
		
		info.url = url.trim();
		
		info.title = getTitle(html);
		info.status = getStatus(html);
		
		String f = "<div id=\"title\"";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<table>";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<tbody>";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<a";
		html = html.substring(html.indexOf(f)+f.length());
		html = html.substring(html.indexOf(">")+1);
		
		String released = html.substring(0, html.indexOf("</a>"));
		released = released.trim();
		
		int year = -1;
		try { year = Integer.parseInt(released); } catch (Exception | Error e1) {}
		
		info.released = year;
		
		f = "<a";
		html = html.substring(html.indexOf(f)+f.length());
		html = html.substring(html.indexOf(">")+1);
		
		String author = html.substring(0, html.indexOf("</a>")).trim();
		
		info.author = Web.clean(author.replace("<", "").replace(">", ""));
		
		f = "<a";
		html = html.substring(html.indexOf(f)+f.length());
		html = html.substring(html.indexOf(">")+1);
		
		String artist = html.substring(0, html.indexOf("</a>")).trim();
		
		info.artist = Web.clean(artist.replace("<", "").replace(">", ""));
		
		f = "<td valign=\"top\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		String genreHtml = html.substring(0, html.indexOf("</td>"));
		
		info.genres.clear();
		
		for (int i = 0; i < 1000 && genreHtml.contains("<a href") && genreHtml.contains("</a>"); i++) {
			
			genreHtml = genreHtml.substring(genreHtml.indexOf("<a href"));
			genreHtml = genreHtml.substring(genreHtml.indexOf(">")+1);
			
			String g = genreHtml.substring(0, genreHtml.indexOf("</a>")).trim();
			
			info.genres.add(Web.clean(g));
			
			genreHtml = genreHtml.substring(genreHtml.indexOf("</a>")+4);
		}
		
		f = "<p class=\"summary less\">";
		
		int ind = html.indexOf(f);
		if (ind == -1) { f = "<p class=\"summary\">"; ind = html.indexOf(f); }
		
		html = html.substring(html.indexOf(f)+f.length());
		
		String synopsis = html.substring(0, html.indexOf("</p>"));
		synopsis = MangaInfo.cleanSynopsis(synopsis);
		
		info.synopsis = synopsis;
		
		return info;
	}
	
	
	public static MangaInfo parseInfo(String url, File mangadir) {
		
		return parseInfoFromHTML(Web.getHTML(url), url, mangadir);
	}
	
	private static MangaInfo parseInfoFromHTML(String html, String url, File mangadir) {
		
		String dtitle = getTitle(html);
		String title = MangaInfo.cleanTitle(dtitle);
		
		File metadata = new File(Main.metaOuts.get(0)+"/"+title.trim()+"/_metadata");
		if (!metadata.exists()) { metadata.mkdirs(); }
		
		File posterdir = new File(metadata.getAbsolutePath().replace("\\", "/")+"/posters");
		if (!posterdir.exists()) { posterdir.mkdirs(); }
		
		for (String metaout : Main.metaOuts) {
		
			File pstrdir = new File(metaout+"/"+title.trim()+"/_metadata/posters");
			
			if (posterdir.listFiles() != null) {
			
				File thumbs = new File(posterdir.getAbsolutePath()+"/thumbs");
				if (!thumbs.exists()) { thumbs.mkdirs(); }
				
				for (File p : posterdir.listFiles()) {
					if (p.isDirectory()) { continue; }
					
					String n = p.getName();
					n = n.substring(0, n.lastIndexOf("."));
					File t = new File(pstrdir.getAbsolutePath()+"/thumbs/"+n+".png");
					
					if (!t.exists()) {
						
						BufferedImage img = null;
						try {
							
							img = ImageIO.read(p);
						} catch (Exception e) {}
						
						if (img != null) {
							
							try {
								Poster.saveThumb(img, t);
							} catch (Exception e) {}
							
						}
						
					}
					
				}
			}
			
		}
		
		if (posterdir.listFiles() == null || posterdir.listFiles().length < 3) {
		
			Thread t = new Thread(){
				@Override
				public void run() {
					
					MAL.downloadPosters(title, title.trim()+"/_metadata/posters", 1);
				}
			};
			t.start();
			
		}
		
		File poster = new File(Main.metaOuts.get(0)+"/"+title.trim()+"/_metadata/posters/01.jpg");
		
		if (poster.exists()) {
			
			for (int i = 1; i < Main.metaOuts.size(); i++) {
				String metaout = Main.metaOuts.get(i);
				
				File o = new File(metaout+"/"+title.trim()+"/_metadata/posters/01.jpg");
				if (o.exists()) { continue; }
				if (!o.getParentFile().exists()) { o.getParentFile().mkdirs(); }
				
				Files.waitOnFile(o, 2);
				Files.copyFileUsingOS(poster, o);
			}
			
		} else {
		
			try {
				
				BufferedImage img = getMangaPoster(html);
				
				if (img != null) {
				
					for (String metaout : Main.metaOuts) {
						
						poster = new File(metaout+"/"+title.trim()+"/_metadata/posters/01.jpg");
						if (poster.exists()) { continue; }
						else { poster.getParentFile().mkdirs(); }
					
						Poster.saveResized(img, poster);
					}
					
				} else { System.out.println(html); }
				
			} catch (Exception e) { e.printStackTrace(); }
			
		}
		
		MangaInfo info = null;
		
		File infoFile = new File(metadata.getAbsolutePath()+"/info.xml");
		
		if (infoFile.exists()) {

			info = MangaInfo.load(infoFile);
			if (info == null) { return new MangaInfo(); }
			
		} else {
		
			info = new MangaInfo();
			
		}
		
		try {
			
			info = getMangaInfo(url, html, info);
			
			saveMangaInfo(info);
			
		} catch (Exception | Error e) { e.printStackTrace(); }
		
		return info;
	}
	
	
	public static void download(String url, File mangadir) {
		
		String html = null;
		String title = null;
		
		Exception exception = null;
		
		for (int i = 0; i < 100 && html == null || getTitle(html) == null; i++) {
			
			html = Web.getHTML(url);
			
			try {
				
				title = getTitle(html);
				
				if (i > 0) { System.out.print("\n"); }
				
			} catch (Exception e) { 
				
				exception = e;

				System.out.print(".");
				
				try { Thread.sleep(50); } catch (Exception e1) {}
			}
		}
		
		if (title == null) {
			
			exception.printStackTrace();
			return;
		}
		
		System.out.println("Downloading or updating \""+title+"\"\n");
		
		MangaInfo mangaInfo = parseInfoFromHTML(html, url, mangadir);
		
		
		// Running javascript to get chapters in spite of the adult content warning
		
		if (html.contains("<div class=\"warning\"")) {
			
			System.out.println("Starting a WebClient to run javascript code to surpass adult-content warnings.");
			System.out.println("Just ignore the following warning(s)..\n");
		
			WebClient client = new WebClient(BrowserVersion.CHROME);
			
			client.getOptions().setThrowExceptionOnScriptError(false);
			client.getOptions().setCssEnabled(false);
			client.getOptions().setThrowExceptionOnFailingStatusCode(false);
			
			client.getCookieManager().setCookiesEnabled(false);
			
			client.setCookieManager(new CookieManager(){
				@Override public void addCookie(Cookie cookie) { }
			});
			
			client.setJavaScriptEngine(new JavaScriptEngine(client){
				@Override protected void handleJavaScriptException(ScriptException arg0, boolean arg1) {}
			});
			
			client.setIncorrectnessListener(new IncorrectnessListener(){
				@Override public void notify(String arg0, Object arg1) { }
			});
			client.setJavaScriptErrorListener(new JavaScriptErrorListener() {
				@Override public void loadScriptError(InteractivePage arg0, URL arg1, Exception arg2) { }
				@Override public void malformedScriptURL(InteractivePage arg0, String arg1, MalformedURLException arg2) { }
				@Override public void scriptException(InteractivePage arg0, ScriptException arg1) { }
				@Override public void timeoutError(InteractivePage arg0, long arg1, long arg2) { }
		    });
			
			try {
				HtmlPage page = client.getPage(url);
				
				System.out.print("\r\n");
				
				//HtmlDivision warning = (HtmlDivision) page.getByXPath("//div[@class='warning']").get(0);
				
				HtmlSpan warningExtra = (HtmlSpan) page.getHtmlElementById("warning_extra");
				HtmlAnchor warningClick = (HtmlAnchor) warningExtra.getHtmlElementDescendants().iterator().next();
				
				String javaScript = warningClick.getAttribute("onClick");
				
				ScriptResult result = page.executeJavaScript(javaScript);
				
				if (!result.getNewPage().isHtmlPage()) { System.out.println("Result wasn't html.."); return; }
				
				page = (HtmlPage) result.getNewPage();
				
				html = page.asXml();
				
			} catch (Exception e) { e.printStackTrace(); }
			
		}
		
		Main.setOutStream(true);
		
		String f = "<div id=\"chapters";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = ">";
		html = html.substring(html.indexOf(f)+f.length());
		
		List<Triplet<String, String, Double>> chapters = new ArrayList<Triplet<String, String, Double>>();
		
		for (int i = 0; i < 10000 && ((html.contains("<h3>") && html.contains("</h3>")) || (html.contains("<h4>") && html.contains("</h4>"))); i++) {
			
			boolean h3 = true;
			
			int ind = html.indexOf("<h3>");
			if (ind == -1 || (html.indexOf("<h4>") != -1 && ind > html.indexOf("<h4>"))) {
				
				h3 = false;
			}
			
			f = h3 ? "<h3>" : "<h4>";
			html = html.substring(html.indexOf(f)+f.length());
			
			f = "<a href=";
			String chapterUrl = html.substring(html.indexOf(f)+f.length()+1, html.indexOf(" title=")-1).trim();
			
			String parsenr = html.substring(html.indexOf(">")+1, html.indexOf("</a>")).trim();
			parsenr = parsenr.substring(title.length()).trim();
			
			double chapterNr = -1;
			
			try {
				
				chapterNr = Double.parseDouble(parsenr);
				
			} catch (Exception | Error e) { try { chapterNr = Integer.parseInt(parsenr); } catch (Exception | Error e1) {} }
			
			html = html.substring(html.indexOf("</a>")+4);
			
			String chapterTitle = "";
			
			f = h3 ? "</h3>" : "</h4>";
			int nindx = html.indexOf(f);
			
			if (nindx != -1 && nindx > html.indexOf("<span") && html.indexOf("<span") != -1) {
				
				chapterTitle = html.substring(html.indexOf(">")+1, html.indexOf("</span>"));
				chapterTitle = chapterTitle.replace("\\", "-");
				chapterTitle = chapterTitle.replace("/", "-");
				chapterTitle = chapterTitle.replace("\"", "");
				chapterTitle = chapterTitle.replace("'", "");
				chapterTitle = chapterTitle.replace("*", "");
				chapterTitle = chapterTitle.replace("?", "");
				chapterTitle = chapterTitle.replace("<", "[");
				chapterTitle = chapterTitle.replace(">", "]");
				chapterTitle = chapterTitle.replace("|", "-");
				chapterTitle = chapterTitle.replace(":", " -");
				chapterTitle = chapterTitle.replace("�", "ss");
				chapterTitle = chapterTitle.replace(".....", "");
				chapterTitle = chapterTitle.replace("....", "");
				chapterTitle = chapterTitle.replace("...", "");
				chapterTitle = chapterTitle.replace("..", "");
				chapterTitle = Web.clean(chapterTitle).trim();
				chapterTitle = chapterTitle.replaceAll("[^ -~]", "");
				
				while (chapterTitle.endsWith(".")) { chapterTitle = chapterTitle.substring(0, chapterTitle.length()-1); }
				
			}
			
			if (chapterNr > 0) { // not saving pilots
			
				if (chapterNr != -1) {
					
					chapters.add(new Triplet<String, String, Double>(chapterTitle, chapterUrl, chapterNr));
					
				} else { System.out.println("Invalid chapter nr \""+parsenr+"\""); }
				
			}
			
			f = h3 ? "</h3>" : "</h4>";
			html = html.substring(html.indexOf(f)+f.length());
		}
		
		if (!chapters.isEmpty()) {
			
			for (int i = chapters.size()-1, n = 1, r = 0, ind = -1; i >= 0; i--) {
				
				if (ind == -1 && chapters.get(i).z == 1.0) {
					
					ind = i;
					
				} else if (ind != -1 && i < ind && chapters.get(i).z == 1.0) {
					
					r = 1;
				}
				
				if (r == 0) {
					
					n++;
					
				} else { chapters.get(i).z += n; }
				
			}
			
			Collections.sort(chapters, new Comparator<Triplet<String, String, Double>>(){

				@Override
				public int compare(Triplet<String, String, Double> arg0, Triplet<String, String, Double> arg1) {
					
					return Double.compare(arg0.z, arg1.z);
				}
				
			});
			
			File dir = getMangasDir(title, mangadir);
			
			List<File> dirFiles = Files.getFiles(dir, f0 -> f0.isDirectory() && !f0.getName().startsWith("_"));
			Collections.sort(dirFiles);
			
			for (int j = 0, lastChapInd = 0; j < chapters.size(); j++) {
				Triplet<String, String, Double> chapter = chapters.get(j);
				
				String chnr = chapter.z+"";
				if (chnr.endsWith(".0")) { chnr = chnr.substring(0, chnr.lastIndexOf(".")); }
				
				String chapnrstr = chnr;
				int l = chapnrstr.length();
				if (chapnrstr.contains(".")) { l -= chapnrstr.substring(chapnrstr.lastIndexOf(".")).length(); }
				for (int i = 0; i < 4 - l; i++) { chapnrstr = "0"+chapnrstr; }
				
				String chapname = chapter.x;//.substring(0, Math.min(chapter.x.length(), 48));
				
				File chapdir = null;
				
				for (int i = lastChapInd; i < dirFiles.size(); i++) {
					File fl = dirFiles.get(i);
					
					if (fl.getName().toLowerCase().trim().startsWith(chapnrstr+" -")) {
						
						chapdir = fl;
						lastChapInd = i;
						
						if (i == dirFiles.size()-1 && !checkChapterComplete(chapdir)) {
							
							Files.cleanseDir(chapdir);
							chapdir.delete();
							
						} else if (chapname != null && chapname.length() > 0 && !chapdir.getName().toLowerCase()
								   .substring(chapdir.getName().indexOf(" -")).trim().equals((" - "+chapname).trim().toLowerCase())) {
							
							System.out.println("Renaming \""+chapdir.getName()+"\" to \""+(chapnrstr+" - "+chapname).trim()+"\"\n");
							
							File nchapdir = new File((chapdir.getParentFile().getAbsolutePath()+"/"+chapnrstr+" - "+chapname).trim()+"/");
							
							for (File file : chapdir.listFiles()) { 
								
								File dst = new File(nchapdir.getAbsolutePath()+"/"+file.getName());
								dst.mkdirs();
								
								Files.waitOnFile(dst, 2);
								
								Files.moveFileUsingOS(file, dst);
								
								for (long m = 0, w = 50; m < 30000 && !dst.canWrite(); m += w) {
									
									try { Thread.sleep(w); } catch (Exception e) {}
								}
								
							}
							
							Files.cleanseDir(chapdir.getAbsolutePath());
							chapdir.delete();
							chapdir = nchapdir;
							
						}
						
						break;
					}
					
				}
				
				if (chapdir == null) { chapdir = new File((dir.getAbsolutePath().replace("\\", "/")+"/"+chapnrstr+" - "+chapname).trim()+"/"); }
				
				if (!chapdir.exists() || (chapdir.isDirectory() && chapdir.listFiles() != null && chapdir.listFiles().length < 1)) {
					
					if (chapdir.exists() || chapdir.mkdirs()) {
					
						System.out.println("Saving chapter "+chnr+" - \""+chapname+"\":\n");
						
						try {
							
							saveChapter(mangaInfo.title, chapname, chapter.y, chapdir);
							
							mangaInfo.recentChapterMillis = System.currentTimeMillis();
							saveMangaInfo(mangaInfo);
							
						} catch (Exception | Error e) { e.printStackTrace(); }
						
						System.out.println();
						
					}
					
				}
				
			}
			
			if (mangaInfo.status.toLowerCase().trim().equals("completed")) {
				
				File dlcomplete = new File(dir.getAbsolutePath()+"/_metadata/DL_COMPLETE");
				try { dlcomplete.createNewFile(); } catch (Exception e) { }
			}
			
			System.out.println("All done.");
			
		} else { System.out.println("No chapters found.."); }
		
	}
	
	private static void saveChapter(String mangaTitle, String chapName, String url, File chapdir) {
		
		//url = "http://m."+url.substring(url.indexOf("mangafox"));
		
		System.out.println("URL: \""+url+"\"");
		
		String html = null;
		int chlength = -1;
		
		for (int i = 0; i < 100 && (chlength == -1); i++) {
			
			html = Web.getDecodedHTML(url, false);
			chlength = getChapterLength(html, false);
		}
		
		if (chlength == -1) { System.out.println("Chapter length unknown..."); return; }
		
		if (chapName.toLowerCase().endsWith("new") && mangaTitle.toLowerCase().trim().equals("bleach")) {
			
			chlength -= 1;
		}
		
		System.out.println("Chapter consists of "+chlength+" pages.");
		
		File lock = new File(chapdir.getAbsolutePath().replace("\\", "/")+"/lock");
		if (!lock.getParentFile().exists()) { lock.getParentFile().mkdirs(); }
		if (!lock.exists()) { try { lock.createNewFile(); } catch (Exception e) {} }
		
		final String urlf = url;
		
		ExecutorService exec = Executors.newCachedThreadPool();
		
		System.out.println();
		
		try {
		
			for (int i = 1; i <= chlength; i++) { // yep that's correct
				
				//System.out.println(url.substring(0, url.lastIndexOf("/")+1)+i+".html");
			
				final int imgnr = i;
				
				exec.submit(new Runnable(){
	
					@Override
					public void run() {
						
						downloadImage(imgnr, urlf, chapdir);
					}
					
				});
				
			}
			
		} finally {
			
			exec.shutdown();
		}
		
		try {
			
			exec.awaitTermination(30, TimeUnit.MINUTES);
			
			lock.delete();
			
		} catch (Exception | Error e) { e.printStackTrace(); }
		
	}
	
	public static void downloadImage(int imgnr, String url, File chapdir) {
		
		for (int k = 0; k < 10000; k++) {
			
			Exception e0 = null;
			
			try {
			
				String imgnrstr = imgnr+"";
				int l = imgnrstr.length();
				for (int j = 0; j < 3 - l; j++) { imgnrstr = "0"+imgnrstr; }
				
				//String imgurl = firstImgUrl.substring(0, firstImgUrl.indexOf(".jpg")-3)+imgnrstr+".jpg";
				
				String html = Web.getDecodedHTML(url.substring(0, url.lastIndexOf("/")+1)+imgnr+".html", false);
				
				String f = "id=\"viewer\">";
				html = html.substring(html.indexOf(f)+f.length());
				
				f = "<div class=\"read_img\">";
				html = html.substring(html.indexOf(f)+f.length());
				
				f = "<a href=";
				html = html.substring(html.indexOf(f)+f.length());
				
				String imgurl = html.substring(html.indexOf("<img src=")+10, html.indexOf(".jpg")+4);
				
				File out = new File(chapdir.getAbsolutePath()+"/"+imgnrstr+".jpg");
				
				if (!out.exists()) {
				
					for (int j = 0; j < 10000; j++) {
					
						Exception e = null;
						
						try {
							
							BufferedImage img = Web.getImage(imgurl);
							
							if (img != null && (img.getWidth() <= 1 || img.getHeight() <= 1)) { break; }
							
							ImageIO.write(img, "jpg", out);
							
							System.out.println("Saved "+out.getName());
							
							break;
							
						} catch (Exception e1) { e = e1; }
						
						try { Thread.sleep(200); } catch (Exception ex) {}
						
						if (j == 9999) { e.printStackTrace(); }
						
					}
					
				}
				
				break;
				
			} catch (Exception e1) { e0 = e1; }
			
			try { Thread.sleep(200); } catch (Exception e) {}
			
			if (k == 9999) { e0.printStackTrace(); }
			
		}
		
	}
	
	
	private static String getTitle(String html) {
		
		String title = "";
		
		String f = "<div id=\"title\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<h2>";
		int h2ind = html.indexOf(f);
		
		if (h2ind == -1 || html.indexOf("</div>") < h2ind) {
			
			f = "<h1";
			html = html.substring(html.indexOf(f)+f.length());
			
			f = ">";
			html = html.substring(html.indexOf(f)+f.length());
			
			title = html.substring(0, html.toLowerCase().indexOf("manga"));
			title = title.toLowerCase().trim();
			title = StringUtils.capitolWords(title);
			
		} else {
			
			html = html.substring(html.indexOf(f)+f.length());
			
			f = "<a href=";
			html = html.substring(html.indexOf(f)+f.length());
			
			title = html.substring(html.indexOf(">")+1, html.indexOf("Manga</a>")-1);
		}
		
		return title;
	}
	
	private static int getChapterLength(String html) {
		
		return getChapterLength(html, true);
	}
	
	private static int getChapterLength(String html, boolean mobile) {
		
		String toparse = "";
		
		try {
		
			if (mobile) {
			
				String f = "<section class=\"main\">";
				html = html.substring(html.indexOf(f)+f.length());
				
				f = "<div class=\"mangaread-main\">";
				html = html.substring(html.indexOf(f)+f.length());
				
				f = "<div class=\"mangaread-operate";
				html = html.substring(html.indexOf(f)+f.length());
				
				f = ">";
				html = html.substring(html.indexOf(f)+f.length());
				
				f = "<select class=\"mangaread-page";
				html = html.substring(html.indexOf(f)+f.length());
				
				f = ">";
				html = html.substring(html.indexOf(f)+f.length());
				
				String options = html.substring(0, html.indexOf("</select>"));
				
				f = "<option";
				options = options.substring(options.lastIndexOf(f)+f.length());
				
				f = ">";
				options = options.substring(options.indexOf(f)+f.length());
				
				toparse = options.substring(0, options.indexOf("</option>"));
				
			} else {
			
				String f = "<div class=\"widepage page\">";
				html = html.substring(html.indexOf(f)+f.length());
				
				f = "<div id=\"top_center_bar\">";
				html = html.substring(html.indexOf(f)+f.length());
				
				f = "<div class=\"r m\">";
				html = html.substring(html.indexOf(f)+f.length());
				
				html = html.substring(html.indexOf("<div class=\"1\">")+14);
				html = html.substring(html.indexOf("</select>"));
				html = html.substring(html.indexOf("of")+3);
				
				toparse = html.substring(0, html.indexOf("<")).trim();
				toparse = toparse.replace("\"", "");
				toparse = toparse.trim();
				
			}
			
		} catch (Exception e) { e.printStackTrace(); }
		
		int chlength = -1;
			
		try {
			chlength = (int)Double.parseDouble(toparse);
		} catch (Exception | Error e) {}
		
		return chlength;
	}
	
	private static boolean checkChapterComplete(File chapdir, String url) {
		
		return checkChapterComplete(chapdir, url, false);
	}
	
	private static boolean checkChapterComplete(File chapdir, String url, boolean lastChapter) {
		
		String html = Web.getDecodedHTML(url, false);
		int chlength = getChapterLength(html);
		
		if (lastChapter) { chlength -= 1; }
		
		List<File> files = Files.getFiles(chapdir, f -> !f.isDirectory() && (f.getName().toLowerCase().endsWith(".jpg") || f.getName().toLowerCase().endsWith(".png")));
		
		return files.size() >= chlength;
	}
	
	
	private static boolean checkChapterComplete(File chapdir) {
		
		File lock = new File(chapdir.getAbsolutePath().replace("\\", "/")+"/lock");
		
		return !lock.exists();
	}
	
	
	private static File getMangasDir(String title, File mangadir) {
		
		File dir = null;
		
		if (mangadir != null && mangadir.exists() && mangadir.isDirectory() && mangadir.listFiles() != null) {
			
			for (File fl : mangadir.listFiles()) {
				
				if (fl.exists() && fl.isDirectory() && fl.getName().toLowerCase().equals(title)) {
					
					dir = fl;
					break;
				}
				
			}
			
		}
		
		if (dir == null) { dir = new File(mangadir.getAbsolutePath()+"/"+title+"/"); dir.mkdirs(); }
		
		return dir;
	}
	
	
	private static String getStatus(String html) {
		
		String f = "<div id=\"series_info\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<div class=\"data\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<h5>status:</h5>";
		html = html.substring(html.toLowerCase().indexOf(f)+f.length());
		
		f = "<span>";
		html = html.substring(html.indexOf(f)+f.length());
		
		String status = html.substring(0, html.indexOf("</span>")).trim();
		if (status.contains(" ")) { status = status.substring(0, status.indexOf(" ")); }
		
		status = status.toLowerCase();
		status = status.replace(".", "").replace(",", "");
		status = status.replaceAll("[^ -~]", "");
		status = status.trim();
		
		return status;
	}
	
	/** Dumps Metadata for currently popular manga into a folder. 
	 * Outdir will be deleted if existent! */
	public static void dumpHot(File outdir) {
		
		
		System.out.println("Dumping metadata for currently popular manga.");
		
		
		if (outdir.exists() && outdir.listFiles() != null && outdir.listFiles().length > 0) {
			
			Files.cleanseDir(outdir);
			
		} else { outdir.mkdirs(); }
		
		File lock = new File(outdir.getAbsolutePath()+"/lock");
		try { lock.createNewFile(); } catch (Exception e) { e.printStackTrace(); }
		
		String html = Web.getHTML("http://mangafox.me/directory/");
		
		String f = "<div id=\"mangalist\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<ul class=\"list\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		
		List<Quad<String, String, String, String>> results = new ArrayList<Quad<String, String, String, String>>(50);
		
		String list = html.substring(0, html.indexOf("</ul>"));
		
		for (int i = 0; i < 50 && list.contains("<li>") && list.contains("</li>"); i++) {
			
			f = "<li>";
			list = list.substring(list.indexOf(f)+f.length());
			
			f = "<img src=";
			list = list.substring(list.indexOf(f)+f.length()+1);
			
			String imgurl = list.substring(0, list.indexOf("width=")-2);
			imgurl = imgurl.replace("\"", "").trim();
			
			f = "<div class=\"manga_text\">";
			list = list.substring(list.indexOf(f)+f.length());
			
			f = "<a class=\"title\" href=";
			list = list.substring(list.indexOf(f)+f.length()+1);
			
			String url = list.substring(0, list.indexOf("rel=")-2);
			url = url.replace("\"", "").trim();
			
			f = ">";
			list = list.substring(list.indexOf(f)+f.length());
			
			String title = list.substring(0, list.indexOf("</a>"));
			title = title.replace("\"", "");
			title = MangaInfo.cleanTitle(title);
			
			f = "<p class=\"info\" title=";
			list = list.substring(list.indexOf(f)+f.length()+1);
			
			String genres = list.substring(0, list.indexOf(">")-1);
			
			results.add(new Quad<String, String, String, String>(title, url, imgurl, genres));
			
			f = "</li>";
			list = list.substring(list.indexOf(f)+f.length());
			
		}
		
		
		for (int i = 0; i < results.size(); i++) {
			Quad<String, String, String, String> result = results.get(i);
			
			File dir = new File(outdir.getAbsolutePath()+"/"+result.x+"/_metadata");
			dir.mkdirs();
			
			File posterdir = new File(dir.getAbsolutePath()+"/posters");
			posterdir.mkdirs();
			
			MangaInfo info = new MangaInfo();
			
			info.title = result.x;
			info.url = result.y;
			
			info.poprank = (i+1);
			
			String[] genres = result.w.trim().split(", ");
			
			for (String g : genres) {
				
				info.genres.add(Web.clean(g.trim()));
			}
			
			info.save(new File(dir.getAbsolutePath()+"/info.xml"));
			
			File notdone = new File(dir.getAbsolutePath()+"/notdone");
			if (!notdone.exists()) { try { notdone.createNewFile(); } catch (Exception e) {} }
			
			File notdoneP = new File(posterdir.getAbsolutePath()+"/notdone");
			if (!notdoneP.exists()) { try { notdoneP.createNewFile(); } catch (Exception e) {} }
			
		}
		
		File timeFile = new File(outdir.getAbsolutePath()+"/time");
		Files.writeText(timeFile, System.currentTimeMillis()+"");
		
		
		System.out.println("Loading posters... almost done.");
		
		
		ExecutorService xctr = Executors.newCachedThreadPool();
		
		try {
		
			for (int j = 0, size = results.size(); j < size; j++) {
				
				Quad<String, String, String, String> result = results.get(j);
				
				xctr.submit(new Runnable(){
					
					@Override
					public void run() {
						
						for (int k = 0; k < 100; k++) {
							
							try {
								
								BufferedImage img = Web.getImage(result.z);
								
								Poster.saveResized(img, new File(outdir.getAbsolutePath()+"/"+result.x+"/_metadata/posters/01.jpg"));
								
								File notdoneP = new File(outdir.getAbsolutePath()+"/"+result.x+"/_metadata/posters/notdone");
								if (notdoneP.exists()) { try { notdoneP.delete(); } catch (Exception e) {} }
								
								break;
								
							} catch (Exception | Error e) { }
						}
						
					}
					
				});
				
				xctr.submit(new Runnable(){
					
					@Override
					public void run() {
					
						File dir = new File(outdir.getAbsolutePath()+"/"+result.x+"/_metadata");
						if (!dir.exists()) { return; }
						
						File file = new File(dir.getAbsolutePath()+"/info.xml");
						if (!file.exists()) { return; }
						
						MangaInfo info = null;
						try { info = MangaInfo.load(file); } 
						catch (Exception | Error e) { return; }
						
						info = getMangaInfo(info.url, info);
						
						info.save(file);
						
						File notdone = new File(dir.getAbsolutePath()+"/notdone");
						if (notdone.exists()) { notdone.delete(); }
						
					}
					
				});
				
			}
		} finally { xctr.shutdown(); }
		
		try {
			
			xctr.awaitTermination(30, TimeUnit.MINUTES);
			
		} catch (Exception | Error e) { e.printStackTrace(); }
		
		lock.delete();
		
		System.out.println("All done.");
		
	}
	
	
	public static void dumpSearch(String search, File tmpdir) {
		
		
		System.out.println("Dumping metadata for search: \""+search+"\"");
		
		
		search = search.replace("\"", "").trim();
		
		File outdir = new File(tmpdir.getAbsolutePath()+"/"+search);
		if (outdir.exists()) {
			
			Files.cleanseDir(outdir);
			
		} else { outdir.mkdirs(); }
		
		File lock = new File(outdir.getAbsolutePath()+"/lock");
		try { lock.createNewFile(); } catch (Exception e1) { e1.printStackTrace(); }
		
		Result[] rs = search(search);
		List<Result> results = new ArrayList<Result>(rs.length);
		for (Result r : rs) { results.add(r); }
		
		sortResults(results, search);
		
		for (int i = 0; i < results.size(); i++) {
			Result result = results.get(i);
			
			File dir = new File(outdir.getAbsolutePath()+"/"+result.title+"/_metadata");
			dir.mkdirs();
			
			File posterdir = new File(dir.getAbsolutePath()+"/posters");
			posterdir.mkdirs();
			
			MangaInfo info = new MangaInfo();
			info.title = result.title;
			info.poprank = (i+1);
			info.url = result.url;
			info.save(new File(dir.getAbsolutePath()+"/info.xml"));
			
			File notdone = new File(dir.getAbsolutePath()+"/notdone");
			if (!notdone.exists()) { try { notdone.createNewFile(); } catch (Exception e) {} }
			
			File notdoneP = new File(posterdir.getAbsolutePath()+"/notdone");
			if (!notdoneP.exists()) { try { notdoneP.createNewFile(); } catch (Exception e) {} }
			
		}
		
		File timeFile = new File(tmpdir.getAbsolutePath()+"/"+search+"/time");
		Files.writeText(timeFile, System.currentTimeMillis()+"");
		
		
		System.out.println("Loading posters.. almost done.");
		
		
		ExecutorService exec = Executors.newCachedThreadPool();
		
		try {
		
			for (Result result : results) {
				
				exec.submit(new Runnable(){
	
					@Override
					public void run() {
						
						String html = null;
						
						for (int i = 0; i < 100 && (html == null || html.trim().length() <= 0); i++) {
							
							html = Web.getHTML(result.url, false);
						}
						
						File f = new File(outdir.getAbsolutePath()+"/"+result.title+"/_metadata/info.xml");
						if (f.exists()) {
							
							MangaInfo info = MangaInfo.load(f);
							if (info != null) {
								
								info = getMangaInfo(result.url, html, info);
								info.save(f);
								
								File notdone = new File(outdir.getAbsolutePath()+"/"+result.title+"/_metadata/notdone");
								if (notdone.exists()) { notdone.delete(); }
							}
							
						}
						
						for (int i = 0; i < 100; i++) {
							
							try {
								
								BufferedImage img = getMangaPoster(html);
								
								Poster.saveResized(img, new File(outdir.getAbsolutePath()+"/"+result.title+"/_metadata/posters/01.jpg"));
								
								File notdoneP = new File(outdir.getAbsolutePath()+"/"+result.title+"/_metadata/posters/notdone");
								if (notdoneP.exists()) { try { notdoneP.delete(); } catch (Exception e) {} }
								
								break;
								
							} catch (Exception | Error e) { }
							
						}
						
					}
					
				});
				
			}
			
		} finally {
			
			exec.shutdown();
		}
		
		try {
			
			exec.awaitTermination(30, TimeUnit.MINUTES);
			
		} catch (Exception | Error e) { e.printStackTrace(); }
		
		lock.delete();
		
		System.out.println("All done.");
		
	}
	
	
	private static void saveMangaInfo(MangaInfo info) {
		
		try {
			
			for (String metaout : Main.metaOuts) {
				
				String s = metaout+"/"+info.title.trim()+"/_metadata/info.xml";
				
				File fl = new File(s);
				if (!fl.exists()) { fl.getParentFile().mkdirs(); }
				else { fl.delete(); }
				
				info.save(fl);
			}
			
		} catch (Exception | Error e) { e.printStackTrace(); }
		
	}
	
	
	@SuppressWarnings("unchecked")
	public static void sortResults(List results, String title) {
		
		String t = title; // �\_(`-`)_/�
		String[] words = t.split(" ");
		
		for (int i = 0; i < words.length; i++) {
			
			words[i] = words[i].trim().toLowerCase();
		}
		
		Collections.sort(results, new Comparator(){

			@Override
			public int compare(Object arg0, Object arg1) {
				
				String s0 = getS(arg0);
				String s1 = getS(arg1);
				
				return compare(s0, s1);
			}
			
			public String getS(Object o) {
				
				if (o instanceof String) {
					
					return ((String)o).trim();
				}
				
				if (o instanceof Result) {
					
					return ((Result)o).title.trim();
				}
				
				if (o instanceof File) {
					
					return ((File)o).getName().trim();
				}
				
				return o.toString();
			}
			
			public int compare(String o1, String o2) {
				
				if (o1.toLowerCase().equals(t)) {
					
					return -1;
				}
				
				if (o2.toLowerCase().equals(t)) {
					
					return 1;
				}
				
				if (o1.toLowerCase().contains(t)) {
					
					return -1;
				}
				
				if (o2.toLowerCase().contains(t)) {
					
					return 1;
				}
				
				for (int i = words.length; i >= 1; i--) {
					
					boolean b1 = StringUtils.containsNum(o1.toLowerCase(), i, words);
					boolean b2 = StringUtils.containsNum(o2.toLowerCase(), i, words);
					
					if (b1 != b2) {
						
						return (b1) ? -1 : 1;
					}
					
				}
				
				return 0;
			}

		});
		
	}
	
	
	public static final String replace = "[{name]}";
	
	public static final String searchUrl = "http://mangafox.me/search.php?name_method=cw&name="+replace
											+"&type=&author_method=cw&author=&artist_method=cw&artist="
											+"&genres%5BAction%5D=0&genres%5BAdult%5D=0&genres%5BAdventure%5D"
											+"=0&genres%5BComedy%5D=0&genres%5BDoujinshi%5D=2&genres%5BDrama%5D="
											+"0&genres%5BEcchi%5D=0&genres%5BFantasy%5D=0&genres%5BGender+Bender"
											+"%5D=0&genres%5BHarem%5D=0&genres%5BHistorical%5D=0&genres%5BHorror%"
											+"5D=0&genres%5BJosei%5D=0&genres%5BMartial+Arts%5D=0&genres%5BMature%"
											+"5D=0&genres%5BMecha%5D=0&genres%5BMystery%5D=0&genres%5BOne+Shot%5D=0"
											+"&genres%5BPsychological%5D=0&genres%5BRomance%5D=0&genres%5BSchool+"
											+"Life%5D=0&genres%5BSci-fi%5D=0&genres%5BSeinen%5D=0&genres%5BShoujo%"
											+"5D=0&genres%5BShoujo+Ai%5D=0&genres%5BShounen%5D=0&genres%5BShounen+Ai%"
											+"5D=0&genres%5BSlice+of+Life%5D=0&genres%5BSmut%5D=0&genres%5BSports%5D=0"
											+"&genres%5BSupernatural%5D=0&genres%5BTragedy%5D=0&genres%5BWebtoons%5D=0&"
											+"genres%5BYaoi%5D=0&genres%5BYuri%5D=0&released_method=eq&released=&rating_"
											+"method=eq&rating=&is_completed=&advopts=1";
	
}