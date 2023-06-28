import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import javax.imageio.ImageIO;


public class Main {
    public static void main(String[] args) {

        ScreenVideoFlvEncoder encoder = new ScreenVideoFlvEncoder();

        boolean useSVC2 = true;

        try {
            BufferedImage image = ImageIO.read(new File("test.png"));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(ScreenVideoEncoder.encodeFlvVideoDataHeader(true, useSVC2));
            bos.write(ScreenVideoEncoder.encodeBlockAndScreenDimensions(16, image.getWidth(), 16, image.getHeight()));
            if (useSVC2)
                bos.write(new byte[] {0}); // reserved and flags
            for (int block_bottom = image.getHeight(); block_bottom > 0; block_bottom -= 16) {
                for (int block_left = 0; block_left < image.getWidth(); block_left += 16) {
                    int width = Math.min(16, image.getWidth() - block_left);
                    int height = Math.min(16, block_bottom);
                    BufferedImage subImage = image.getSubimage(block_left, block_bottom - height, width, height);
                    int[] pixels = ScreenVideoEncoder.getPixels(subImage, 0, 0, subImage.getWidth(), subImage.getHeight(), useSVC2);

                    if (useSVC2)
                        bos.write(ScreenVideoEncoder.encodePixelsSVC2(pixels, subImage.getWidth(), subImage.getHeight()));
                    else
                        bos.write(ScreenVideoEncoder.encodePixels(pixels, subImage.getWidth(), subImage.getHeight(), false));
                }
            }

            FileOutputStream fos = new FileOutputStream("test.flv");
            fos.write(encoder.encodeHeader());
            fos.write(encoder.encodeFlvData(bos.toByteArray()));
            fos.close();
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }
}