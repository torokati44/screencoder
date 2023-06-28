# Flash Screen Video Encoder

This is a very basic encoder for Flash Screen Video (V1 and V2) files into FLV container.

It encodes with a fixed 16x16 block size, only outputs keyframes, with 30 FPS.
It also doesn't fully utilize the features of the V2 format.

The original files (except for `Main.java` and this README) were salvaged from here, under the GNU LGPL (version 2.1 or 3.0) license:

https://github.com/bigbluebutton/bigbluebutton/tree/c868beed6a6cbf886c6c13ed6bb0c8256507c50a

They were then cleaned up and modified by me.

### Building

`$ javac *.java`

### Running

`$ java Main <args>`

Run without any arguments to see help on usage.

### Converting to SWF

The output, at least V1, can be embedded into an SWF if needed, by running:

`$ ffmpeg -i video.flv -vcodec copy -r <FPS> video.swf`
