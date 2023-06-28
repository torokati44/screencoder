Flash Screen Video Encoder
===

This is a very basic encoder for Flash Screen Video (V1 and V2) files into FLV container.

It doesn't fully utilize the features of the V2 format.

The output can then be embedded into `.swf` if needed, by using:

`$ ffmpeg -i video.flv -vcodec copy -r <FPS> video.swf`

The original files (except for `Main.java`) were salvaged from here, under the LGPL 2.1 license:

https://github.com/bigbluebutton/bigbluebutton/tree/c868beed6a6cbf886c6c13ed6bb0c8256507c50a

They were then cleaned up and modified by me.