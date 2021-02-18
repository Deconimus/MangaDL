package main;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import visionCore.util.Web;

public class MangaSeeOnline {

	public static void saveChapter(String url, File chapdir) {
		
		System.out.println("URL: \""+url+"\"");
		
		if (!chapdir.exists()) { chapdir.mkdirs(); }
		
		File lock = new File(chapdir.getAbsolutePath().replace("\\", "/")+"/lock");
		if (!lock.getParentFile().exists()) { lock.getParentFile().mkdirs(); }
		if (!lock.exists()) { try { lock.createNewFile(); } catch (Exception e) {} }
		
		if (url.contains("-page-")) {
			
			url = url.substring(0, url.lastIndexOf("-page-"))+".html";
		}
		
		String html = Web.getHTML(url, false);
		String f = "";
		
		f = "<div class=\"image-container\">";
		html = html.substring(html.indexOf(f)+f.length());
		
		f = "<div style=";
		html = html.substring(0, html.indexOf(f));
		
		List<String> imgurls = new ArrayList<String>();
		
		f = "<div class=\"fullchapimage";
		String f1 = "<div class='fullchapimage";
		
		while (html.contains(f) || html.contains(f1)) {
			
			int ind = html.indexOf(f);
			if (ind == -1) { ind = html.indexOf(f1); }
			
			html = html.substring(ind+f.length());
			html = html.substring(html.indexOf("<img src=")+10);
			
			String imgurl = html.substring(0, html.indexOf(">")-1);
			
			imgurls.add(imgurl);
		}
		
		System.out.println("Chapter consists of "+imgurls.size()+" pages.\n");
		
		ExecutorService exec = Executors.newCachedThreadPool();
		
		try {
		
			for (int i = 0; i < imgurls.size(); i++) {
				
				final int nr = i+1;
				final String imgUrl = imgurls.get(i);
				final File chapd = chapdir;
				
				try {
					
					exec.submit(new Runnable(){
						
						@Override
						public void run() {
							
							saveImage(nr, imgUrl, chapd);
						}
					});
					
				} catch (Exception e) { e.printStackTrace(); }
			}
			
		} finally {
			
			exec.shutdown();
		}
		
		try {
			
			exec.awaitTermination(30, TimeUnit.MINUTES);
			
			lock.delete();
			
		} catch (Exception | Error e) { e.printStackTrace(); }
	}
	
	private static void saveImage(int nr, String url, File chapdir) {
		
		String nrStr = nr+"";
		for (int i = 0, l = 3-nrStr.length(); i < l; i++) { nrStr = "0"+nrStr; }
		
		File out = new File(chapdir.getAbsolutePath().replace("\\", "/")+"/"+nrStr+".jpg");
		
		for (int i = 0; i < 100; i++) {
		
			try {
			
				Web.downloadFile(url, out);
				
				System.out.println("Saved "+out.getName());
				
				break;
				
			} catch (Exception | Error e) { if (i == 99) { e.printStackTrace(); } }
			
			try { Thread.sleep(150); } catch (Exception e1) {}
			
		}
	}
	
}
