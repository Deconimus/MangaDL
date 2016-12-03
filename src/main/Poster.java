package main;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import visionCore.util.Files;

public class Poster {

	public static final float WIDTH = 256f, HEIGHT = 398f;
	
	public static void saveResized(BufferedImage img, File out) throws Exception {
	
		BufferedImage imgout = new BufferedImage((int)WIDTH, (int)HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics g = imgout.getGraphics();
		
		float sw = WIDTH / (float)img.getWidth();
		float sh = HEIGHT / (float)img.getHeight();
		
		float scale = Math.max(sw, sh);
		
		float w = img.getWidth() * scale;
		float h = img.getHeight() * scale;
		
		float x = (WIDTH - w) * 0.5f;
		float y = (HEIGHT - h) * 0.5f;
		
		g.drawImage(img.getScaledInstance((int)(w + 0.5f), (int)(h + 0.5f), Image.SCALE_SMOOTH), (int)x, (int)y, (int)(w + 0.5f), (int)(h + 0.5f), null);
		
		Files.waitOnFile(out, 2);
		ImageIO.write(imgout, "jpg", out);
		
	}
	
}
