
/**
*
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
*
* Copyright (c) 2010 BigBlueButton Inc. and by respective authors (see below).
*
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License as published by the Free Software
* Foundation; either version 2.1 of the License, or (at your option) any later
* version.
*
* BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License along
* with BigBlueButton; if not, see <http://www.gnu.org/licenses/>.
*
**/

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public final class ScreenVideoEncoder {

    private static byte FLV_KEYFRAME = 0x10;
    private static byte FLV_INTERFRAME = 0x20;
    private static byte SCREEN_VIDEO_CODEC_ID = 0x03;
    private static byte SCREEN_VIDEO_CODEC_V2_ID = 0x06;

    private static final int PALETTE[] = {
            0x000000, 0x333333, 0x666666, 0x999999, 0xCCCCCC, 0xFFFFFF,

            0x330000, 0x660000, 0x990000, 0xCC0000, 0xFF0000,
            0x003300, 0x006600, 0x009900, 0x00CC00, 0x00FF00,
            0x000033, 0x000066, 0x000099, 0x0000CC, 0x0000FF,

            0x333300, 0x666600, 0x999900, 0xCCCC00, 0xFFFF00,
            0x003333, 0x006666, 0x009999, 0x00CCCC, 0x00FFFF,
            0x330033, 0x660066, 0x990099, 0xCC00CC, 0xFF00FF,

            0xFFFF33, 0xFFFF66, 0xFFFF99, 0xFFFFCC,
            0xFF33FF, 0xFF66FF, 0xFF99FF, 0xFFCCFF,
            0x33FFFF, 0x66FFFF, 0x99FFFF, 0xCCFFFF,

            0xCCCC33, 0xCCCC66, 0xCCCC99, 0xCCCCFF,
            0xCC33CC, 0xCC66CC, 0xCC99CC, 0xCCFFCC,
            0x33CCCC, 0x66CCCC, 0x99CCCC, 0xFFCCCC,

            0x999933, 0x999966, 0x9999CC, 0x9999FF,
            0x993399, 0x996699, 0x99CC99, 0x99FF99,
            0x339999, 0x669999, 0xCC9999, 0xFF9999,

            0x666633, 0x666699, 0x6666CC, 0x6666FF,
            0x663366, 0x669966, 0x66CC66, 0x66FF66,
            0x336666, 0x996666, 0xCC6666, 0xFF6666,

            0x333366, 0x333399, 0x3333CC, 0x3333FF,
            0x336633, 0x339933, 0x33CC33, 0x33FF33,
            0x663333, 0x993333, 0xCC3333, 0xFF3333,

            0x003366, 0x336600, 0x660033, 0x006633, 0x330066, 0x663300,
            0x336699, 0x669933, 0x993366, 0x339966, 0x663399, 0x996633,
            0x6699CC, 0x99CC66, 0xCC6699, 0x66CC99, 0x9966CC, 0xCC9966,
            0x99CCFF, 0xCCFF99, 0xFF99CC, 0x99FFCC, 0xCC99FF, 0xFFCC99,

            0x111111, 0x222222, 0x444444, 0x555555,
            0xAAAAAA, 0xBBBBBB, 0xDDDDDD, 0xEEEEEE,
    };

    private static final int C7_C15_THRESHOLD = 15;

    private static int[] paletteIndex; // maps c15 -> c7 (2^15=32768; palette size: 2^7=128)

    public static byte encodeFlvVideoDataHeader(boolean isKeyFrame, boolean useSVC2) {
        return (byte) ((isKeyFrame ? FLV_KEYFRAME : FLV_INTERFRAME)
                + (useSVC2 ? SCREEN_VIDEO_CODEC_V2_ID : SCREEN_VIDEO_CODEC_ID));
    }

    public static byte[] encodeBlockAndScreenDimensions(int blockWidth, int imageWidth, int blockHeight,
            int imageHeight) {
        byte[] dims = new byte[4];

        int bw = (((blockWidth / 16) - 1) & 0xf) << 12;
        int iw = (imageWidth & 0xfff);
        int ew = (bw | iw);

        int bh = (((blockHeight / 16) - 1) & 0xf) << 12;
        int ih = (imageHeight & 0xfff);
        int eh = (bh | ih);

        dims[0] = (byte) ((ew & 0xff00) >> 8);
        dims[1] = (byte) (ew & 0xff);
        dims[2] = (byte) ((eh & 0xff00) >> 8);
        dims[3] = (byte) (eh & 0xff);

        return dims;
    }

    public static int[] getPixels(BufferedImage image, int x, int y, int width, int height) {
        return image.getRGB(x, y, width, height, null, 0, width);
    }

    public static byte[] encodePixels(int pixels[], int width, int height) {

        changePixelScanFromBottomLeftToTopRight(pixels, width, height);

        byte[] bgrPixels = convertFromRGBtoBGR(pixels);

        byte[] compressedPixels = compressUsingZlib(bgrPixels);

        byte[] encodedDataLength = ScreenVideoEncoder.encodeCompressedPixelsDataLength(compressedPixels.length);
        byte[] encodedData = new byte[encodedDataLength.length + compressedPixels.length];

        System.arraycopy(encodedDataLength, 0, encodedData, 0, encodedDataLength.length);

        System.arraycopy(compressedPixels, 0, encodedData, encodedDataLength.length, compressedPixels.length);

        return encodedData;
    }

    private static byte[] encodeCompressedPixelsDataLength(int length) {
        int byte1 = ((length & 0xFF00) >> 8);
        int byte2 = (length & 0x0FFF);
        // System.out.println("Block size = " + length + " hex=" +
        // Integer.toHexString(length) + " bytes= " + Integer.toHexString(byte1) + " " +
        // Integer.toHexString(byte2));
        return new byte[] { (byte) byte1, (byte) (byte2 & 0xFFF) };
    }

    public static byte[] encodeBlockUnchanged() {
        return new byte[] { (byte) 0, (byte) 0 };
    }

    /**
     * Screen capture pixels are arranged top-left to bottom-right. ScreenVideo
     * encoding
     * expects pixels are arranged bottom-left to top-right.
     *
     * @param pixels - contains pixels of the image
     * @param width  - width of the image
     * @param height - height of the image
     */
    private static void changePixelScanFromBottomLeftToTopRight(int[] pixels, int width, int height) {
        int[] swap = new int[pixels.length];

        for (int i = 0; i < height; i++) {
            int sourcePos = i * width;
            int destPos = (height - (i + 1)) * width;
            System.arraycopy(pixels, sourcePos, swap, destPos, width);
        }

        System.arraycopy(swap, 0, pixels, 0, pixels.length);
    }

    /**
     * Compress the byte array using Zlib.
     *
     * @param pixels
     * @return a byte array of compressed data
     */
    private static byte[] compressUsingZlib(byte[] pixels) {
        // Create the compressed stream
        byte[] output = new byte[pixels.length];
        Deflater compresser = new Deflater(Deflater.BEST_COMPRESSION);
        compresser.setInput(pixels);
        compresser.finish();
        int compressedDataLength = compresser.deflate(output);

        byte[] zData = new byte[compressedDataLength];
        System.arraycopy(output, 0, zData, 0, compressedDataLength);

        // set the byte array to the newly compressed data
        return zData;
    }

    /**
     * Extracts the RGB bytes from a pixel represented by a 4-byte integer (ARGB).
     *
     * @param pixels
     * @return pixels in BGR order
     */
    private static byte[] convertFromRGBtoBGR(int[] pixels) {
        byte[] rgbPixels = new byte[pixels.length * 3];
        int position = 0;

        for (int i = 0; i < pixels.length; i++) {
            byte red = (byte) ((pixels[i] >> 16) & 0xff);
            byte green = (byte) ((pixels[i] >> 8) & 0xff);
            byte blue = (byte) (pixels[i] & 0xff);

            // Sequence should be BGR
            rgbPixels[position++] = blue;
            rgbPixels[position++] = green;
            rgbPixels[position++] = red;
        }

        return rgbPixels;
    }

    public static String toStringBits(int value) {
        int displayMask = 1 << 31;
        StringBuffer buf = new StringBuffer(35);

        for (int c = 1; c <= 32; c++) {
            buf.append((value & displayMask) == 0 ? '0' : '1');
            value <<= 1;

            if (c % 8 == 0)
                buf.append(' ');
        }

        return buf.toString();
    }

    public static String toStringBits(byte value) {
        int displayMask = 1 << 7;
        StringBuffer buf = new StringBuffer(8);

        for (int c = 1; c <= 8; c++) {
            buf.append((value & displayMask) == 0 ? '0' : '1');
            value <<= 1;

            if (c % 8 == 0)
                buf.append(' ');
        }

        return buf.toString();
    }

    public static byte[] encodePixelsSVC2(int pixels[], int width, int height) {
        changePixelScanFromBottomLeftToTopRight(pixels, width, height);

        // write the block as IMAGEBLOCKV2

        if (paletteIndex == null)
            createPaletteIndex();

        try {
            ByteArrayOutputStream baos1 = new ByteArrayOutputStream(); // TODO calibrate initial size

            for (int i = 0; i < pixels.length; i++) {
                writeAs15_7(pixels[i], baos1);
            }

            // baos2 contains everything from IMAGEBLOCKV2 except the DataSize field
            ByteArrayOutputStream baos2 = new ByteArrayOutputStream(); // TODO calibrate initial size

            // IMAGEFORMAT:
            // ColorDepth: UB[2] 10 (15/7 hybrid color image); HasDiffBlocks: UB[1] 0; Zlib
            // prime stuff (2 bits) not used (0)
            baos2.write(16);

            // No ImageBlockHeader (IMAGEDIFFPOSITION, IMAGEPRIMEPOSITION)

            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            DeflaterOutputStream deflateroutputstream = new DeflaterOutputStream(baos2, deflater);
            deflateroutputstream.write(baos1.toByteArray());
            deflateroutputstream.finish();
            byte dataBuffer[] = baos2.toByteArray();

            // DataSize field
            int dataSize = dataBuffer.length;
            ByteArrayOutputStream baos = new ByteArrayOutputStream(); // TODO calibrate initial size
            writeShort(((OutputStream) (baos)), dataSize);
            // Data
            baos.write(dataBuffer, 0, dataSize);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeShort(OutputStream outputstream, int l) throws IOException {
        outputstream.write(l >> 8 & 0xff);
        outputstream.write(l & 0xff);
    }

    private static int chromaDifference(int c1, int c2) {
        int t1 = (c1 & 0x000000ff) + ((c1 & 0x0000ff00) >> 8) + ((c1 & 0x00ff0000) >> 16);
        int t2 = (c2 & 0x000000ff) + ((c2 & 0x0000ff00) >> 8) + ((c2 & 0x00ff0000) >> 16);
        return Math.abs(t1 - t2) + Math.abs((c1 & 0x000000ff) - (c2 & 0x000000ff))
                + Math.abs(((c1 & 0x0000ff00) >> 8) - ((c2 & 0x0000ff00) >> 8))
                + Math.abs(((c1 & 0x00ff0000) >> 16) - ((c2 & 0x00ff0000) >> 16));
    }

    private static int computePaletteIndexForColor(int rgb) {
        int min = 0x7fffffff;
        int minc = -1;
        for (int i = 0; i < 128; i++) {
            int diff = chromaDifference(PALETTE[i], rgb);
            if (diff < min) {
                min = diff;
                minc = i;
            }
        }
        return minc;
    }

    private static int createPaletteIndex() {
        paletteIndex = new int[32768];
        for (int r = 4; r < 256; r += 8) {
            for (int g = 4; g < 256; g += 8) {
                for (int b = 4; b < 256; b += 8) {
                    int rgb = b | (g << 8) | (r << 16);
                    int c15 = (b >> 3) | ((g & 0xf8) << 2) | ((r & 0xf8) << 7);
                    int index = computePaletteIndexForColor(rgb);
                    paletteIndex[c15] = index;
                }
            }
        }
        return 0;
    }

    private static void writeAs15_7(int rgb, ByteArrayOutputStream stream) throws IOException {
        // convert from 24 bit RGB to 15 bit RGB
        int c15 = ((rgb & 0xf80000) >> 9 | (rgb & 0xf800) >> 6 | (rgb & 0xf8) >> 3) & 0x7fff;
        int d15 = chromaDifference(rgb, rgb & 0x00f8f8f8);
        int c7 = paletteIndex[c15];
        int d7 = chromaDifference(rgb, PALETTE[c7]);
        if (d7 - d15 <= C7_C15_THRESHOLD) {
            // write c7, c15 isn't much better
            stream.write(c7);
        } else {
            writeShort(stream, 0x8000 | c15); // high bit set as marker for c15
        }
    }
}
