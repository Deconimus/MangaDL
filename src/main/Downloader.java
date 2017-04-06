package main;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import mangaLib.MangaInfo;
import mangaLib.Poster;
import mangaLib.scrapers.Scraper;
import visionCore.dataStructures.tuples.Triplet;
import visionCore.util.ArrayUtils;
import visionCore.util.Files;
import visionCore.util.Web;

public class Downloader {
	
	
	public static void download(MangaInfo info, String url) {
		
		String html = Web.tryGetHTML(url, 100, 60 * 1000, false);
		
		Scraper scraper = Scraper.getScraper(url);
		info = scraper.getInfo(url, html, info);
		
		File mangadir = new File(Main.mangapath+"/"+info.title);
		if (!mangadir.exists()) { mangadir.mkdirs(); }
		
		for (String metaPath : Main.metaOuts) {
			
			File dir = new File(metaPath);
			if (!dir.exists()) { continue; }
			
			File metaDir = new File(metaPath+"/"+info.title+"/_metadata");
			if (!metaDir.exists()) { metaDir.mkdirs(); }
			
			info.save(new File(metaDir.getAbsolutePath().replace('\\', '/')+"/info.xml"));
		}
		
		
		System.out.println("Downloading or updating \""+info.title+"\"\n");
		
		
		final MangaInfo pinfo = info.copy();
		Thread t = new Thread(){
			
			{ setDaemon(true); }
			
			@Override
			public void run() {
				
				downloadPosters(pinfo, url, html);
			}
		};
		t.start();
		
		
		List<Triplet<String, Double, String>> chapters = scraper.getChapters(html);
		
		HashMap<Double, File> completedChapters = new HashMap<Double, File>();
		
		for (File f : mangadir.listFiles()) {
			
			String fn = f.getName();
			if (!f.isDirectory() || fn.startsWith("_") || fn.startsWith(".")) { continue; }
			
			String nrs = fn.substring(0, fn.indexOf(" -"));
			double nr = -1;
			
			try { nr = Double.parseDouble(nrs); } 
			catch (Exception | Error e) { continue; }
			
			File lock = new File(f.getAbsolutePath().replace('\\', '/')+"/lock");
			
			if (f.listFiles().length > 0 && !lock.exists()) {
				
				completedChapters.put(nr, f);
				
			} else {
				
				Files.deleteDir(f);
			}
		}
		
		if (info.chsubs != null) {
			
			substituteChapters(chapters, info.chsubs);
		}
		
		for (Triplet<String, Double, String> chapter : chapters) {
			
			if (completedChapters.containsKey(chapter.y)) { 
				
				File f = completedChapters.get(chapter.y);
				String ft = f.getName();
				ft = ft.substring(ft.indexOf(" -")+2).trim();
				
				if (!chapter.z.trim().isEmpty() && !chapter.z.trim().toLowerCase().equals("new") 
					&& !ft.equalsIgnoreCase(chapter.z) && !(new File(f.getAbsolutePath().replace('\\', '/')+"/.renamed").exists())) {
					
					String nn = (f.getName().substring(0, f.getName().indexOf(" - "))+" - "+chapter.z.trim()).trim();
					File nd = new File(f.getParentFile().getAbsolutePath().replace('\\', '/')+"/"+nn);
					
					System.out.println("Renaming Ch."+chapter.y+" to \""+nn+"\".");
					
					Files.moveDir(f, nd);
				}
				
				continue;
			}
			
			String chapNrStr = (chapter.y == (int)((double)chapter.y)) ? ""+((int)((double)chapter.y)) : ""+chapter.y;
			String nrs = chapNrStr;
			
			for (int i = 0, l = (""+(int)((double)chapter.y)).length(); i < 4-l; i++) {
				
				chapNrStr = "0"+chapNrStr;
			}
			
			String chapdirName = chapNrStr+" - "+chapter.z.trim();
			chapdirName = chapdirName.trim();
			
			File chapdir = new File(mangadir.getAbsolutePath().replace('\\', '/')+"/"+chapdirName);
			if (!chapdir.exists()) { chapdir.mkdirs(); }
			
			System.out.println("Saving chapter "+nrs+" - \""+chapter.z.trim()+"\":\n");
			System.out.println("URL: \""+chapter.x+"\"");
			
			downloadChapter(chapter.x, chapdir);
		}
		
		if (chapters.isEmpty()) { System.out.println("No chapters found.."); }
		else {
			
			if (info.status.toLowerCase().startsWith("complete")) {
				
				File dlcomplete = new File(Main.mangapath+"/"+info.title+"/_metadata/DL_COMPLETE");
				try { dlcomplete.createNewFile(); } catch (Exception | Error e) {}
			}
			
			System.out.println("All done.");
		}
		
	}
	
	
	public static void downloadChapter(String url, File chapdir) {
		
		downloadChapter(Scraper.getScraper(url), url, chapdir);
	}
	
	public static void downloadChapter(Scraper scraper, String url, File chapdir) {
		
		List<String> imgUrls = scraper.getChapterImgUrls(url);
		
		int chlength = imgUrls.size();
		if (chlength <= 0) { System.out.println("Chapter is empty.\n"); return; }
		
		System.out.println("Chapter consists of "+chlength+" pages.");
		
		File lock = new File(chapdir.getAbsolutePath().replace("\\", "/")+"/lock");
		if (!lock.getParentFile().exists()) { lock.getParentFile().mkdirs(); }
		if (!lock.exists()) { try { lock.createNewFile(); } catch (Exception e) {} }
		
		ExecutorService exec = Executors.newCachedThreadPool();
		
		System.out.println();
		
		try {
		
			for (int i = 0; i < chlength; i++) {
				
				final int imgnr = i+1;
				final String urlf = imgUrls.get(i);
				
				exec.submit(new Runnable(){
	
					@Override
					public void run() { downloadImage(imgnr, urlf, chapdir); }
				});
			}
			
		} finally { exec.shutdown(); }
		
		try {
			
			exec.awaitTermination(30, TimeUnit.MINUTES);
			lock.delete();
			
		} catch (Exception | Error e) { e.printStackTrace(); }
		
		System.out.println();
		
	}
	
	
	public static void downloadImage(int imgnr, String url, File chapdir) {
		
		String ext = ".jpg";
		if (url.toLowerCase().endsWith(".jpg")) { ext = ".jpg"; }
		else if (url.toLowerCase().endsWith(".png")) { ext = ".png"; }
		else if (url.toLowerCase().contains(".png")) { ext = ".png"; }
		
		String imgnrStr = ""+imgnr;
		for (int i = 0, l = imgnrStr.length(); i < 3-l; i++) { imgnrStr = "0"+imgnrStr; }
		
		File file = new File(chapdir.getAbsolutePath().replace('\\', '/')+"/"+(imgnrStr)+ext);
		file = Web.downloadFile(url, file, true);
		
		System.out.println("Saved "+file.getName());
	}
	
	
	public static void substituteChapters(List<Triplet<String, Double, String>> chapters, String chsubs) {
		
		if (chsubs.toLowerCase().contains("-chapter")) {
			
			chsubs = chsubs.substring(chsubs.toLowerCase().indexOf("-chapter"));
		}
		
		String html = Web.getHTML(chsubs);
		List<Triplet<String, Double, String>> subs = Scraper.getScraper(chsubs).getChapters(html);
		
		int subsLen = subs.size();
		int off = 0;
		
		for (Triplet<String, Double, String> chapter : chapters) {
			
			inner:
			for (int i = off; i < subsLen; i++) {
				Triplet<String, Double, String> chsub = subs.get(i);
				
				if ((double)chapter.y == (double)chsub.y) {
					
					chapter.x = chsub.x;
					
					off = i+1;
					break inner;
					
				} else if ((double)chapter.y < (double)chsub.y) {
					
					break inner;
				}
			}
		}
	}
	
	
	public static void downloadPosters(MangaInfo info, String url, String html) {
		
		Scraper scraper = Scraper.getScraper(url);
		
		File posterdir = new File(Main.metaOuts.get(0).replace('\\', '/')+"/"+info.title+"/_metadata/posters");
		if (!posterdir.exists()) { posterdir.mkdirs(); }
		
		File poster1 = null;
		String poster1Str = "/"+info.title+"/_metadata/posters/01";
		
		for (String mp : Main.metaOuts) {
			
			File f = new File(mp+poster1Str+".jpg");
			File f1 = new File(mp+poster1Str+".png");
			
			if (f.exists()) { poster1 = f; break; }
			if (f1.exists()) { poster1 = f1; break; }
		}
		
		if (poster1 != null) {
			
			String ext = Files.getExtension(poster1);
			
			for (String mp : Main.metaOuts) {
				
				File f = new File(mp+poster1Str+"."+ext);
				if (!f.getParentFile().exists()) { f.getParentFile().mkdirs(); }
				if (f.exists()) { continue; }
				
				try { Files.copy(poster1, f); } catch (Exception e) {}
			}
			
		} else {
			
			if (html == null) { html = Web.getHTML(url, false); }
			
			String posterUrl = scraper.getPosterUrl(html);
			
			String ext = ".jpg";
			if (posterUrl.toLowerCase().contains(".png")) { ext = ".png"; } // cutting won't CUT it here
			
			poster1 = new File(Main.metaOuts.get(0)+poster1Str+ext);
			Web.downloadFile(posterUrl, poster1, true);
			
			try { Thread.sleep(50); } catch (Exception e) {}
			
			for (int i = 0; i < 100 && (!poster1.canRead() || !poster1.exists()); i++) {
				
				try { Thread.sleep(50); } catch (Exception e) {}
			}
			
			for (int i = 1; i < Main.metaOuts.size(); i++) {
				
				File f = new File(Main.metaOuts.get(i)+poster1Str+"."+Files.getExtension(poster1));
				if (!f.getParentFile().exists()) { f.getParentFile().mkdirs(); }
				
				try { Files.copy(poster1, f); } catch (Exception e) {}
			}
		}
		
		if (posterdir.list().length < 3) {
			
			if (info.mal_id >= 0) { MAL.downloadPosters(info.mal_id, info.title+"/_metadata/posters", 1); }
			else { MAL.downloadPosters(info.title, info.title+"/_metadata/posters", 1); }
		}
		
		
		// create thumbnails
		
		File thumbdir = new File(posterdir.getAbsolutePath().replace('\\', '/')+"/thumbs");
		
		for (File f : posterdir.listFiles()) {
			
			String fn = f.getName();
			String fnlc = fn.toLowerCase();
			if (f.isDirectory() || (!fnlc.endsWith(".jpg") && !fnlc.endsWith(".png"))) { continue; }
			
			try {
			
				BufferedImage img = ImageIO.read(f);
				File thumb = new File(thumbdir.getAbsolutePath().replace('\\', '/')+"/"+fn.substring(0, fn.lastIndexOf('.'))+".png");
				
				Poster.saveThumb(img, thumb);
			
			} catch (Exception | Error e) {}
		}
		
		for (int i = 1; i < Main.metaOuts.size(); i++) {
			String mp = Main.metaOuts.get(i);
			
			File d = new File(mp+"/"+info.title+"/_metadata/posters/thumbs");
			Files.copyDir(thumbdir, d);
		}
		
	}
	
	
}
