import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import javax.imageio.ImageIO;

public class Main {
    public static void printUsage() {
        System.out.println("Usage:\n\njava Main [-v2] frame1.png [frame2.png...] video.flv");
    }

    public static void main(String[] args) {
        boolean useSVC2 = false;

        if (args.length > 0 && args[0].equals("-v2")) {
            useSVC2 = true;
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }

        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        // each must be a multiple of 16, and at most 256
        int blockWidth = 16;
        int blockHeight = 16;

        try {
            ScreenVideoFlvEncoder encoder = new ScreenVideoFlvEncoder();

            FileOutputStream fos = new FileOutputStream(args[args.length - 1]);
            fos.write(encoder.encodeHeader());

            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            for (int i = 0; i < args.length - 1; i++) {
                System.out.println("Encoding " + args[i]);
                bos.reset();
                bos.write(ScreenVideoEncoder.encodeFlvVideoDataHeader(true, useSVC2));

                BufferedImage image = ImageIO.read(new File(args[i]));
                bos.write(ScreenVideoEncoder.encodeBlockAndScreenDimensions(blockWidth, image.getWidth(), blockHeight, image.getHeight()));

                if (useSVC2)
                    bos.write(new byte[] {0}); // reserved bits and some flags

                for (int blockBottom = image.getHeight(); blockBottom > 0; blockBottom -= blockHeight) {
                    for (int blockLeft = 0; blockLeft < image.getWidth(); blockLeft += blockWidth) {
                        int width = Math.min(blockWidth, image.getWidth() - blockLeft);
                        int height = Math.min(blockHeight, blockBottom);

                        BufferedImage subImage = image.getSubimage(blockLeft, blockBottom - height, width, height);

                        int[] pixels = ScreenVideoEncoder.getPixels(subImage, 0, 0, width, height, useSVC2);
                        if (useSVC2)
                            bos.write(ScreenVideoEncoder.encodePixelsSVC2(pixels, width, height));
                        else
                            bos.write(ScreenVideoEncoder.encodePixels(pixels, width, height, false));
                    }
                }

                fos.write(encoder.encodeFlvData(bos.toByteArray()));
            }
            fos.close();

            System.out.println("Done.");
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(2);
        }
    }
}