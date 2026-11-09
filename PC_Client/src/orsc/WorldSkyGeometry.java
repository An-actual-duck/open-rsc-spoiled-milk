package orsc;

/** Shared accepted world-dome primitive/color generator. Native emission retains
 * the exact GL stream; the browser caches full geometry and updates color slots.
 * An instance is single-presenter-owned and must not be used concurrently. */
final class WorldSkyGeometry {
    interface Sink {
        int GL_BLEND=0x0BE2,GL_SRC_ALPHA=0x0302,GL_ONE_MINUS_SRC_ALPHA=0x0303,GL_TRIANGLES=4,GL_QUADS=7;
        void layer(int layer) throws Exception;
        void glEnable(int value) throws Exception;
        void glDisable(int value) throws Exception;
        void glBlendFunc(int source,int destination) throws Exception;
        void glBegin(int mode) throws Exception;
        void glEnd() throws Exception;
        void glColor4f(int palette,float red,float green,float blue,float alpha) throws Exception;
        void glVertex3f(float x,float y,float z) throws Exception;
    }
    private Sink gl;
    private boolean colorsOnly,allLayers;
    private int paletteCursor;
	private static final int SKY_DOME_SEGMENTS = 40;
	private static final float[] SKY_DOME_ELEVATION_DEGREES = new float[] {
		-90.0f, -68.0f, -48.0f, -30.0f, -15.0f, 0.0f, 12.0f, 30.0f, 60.0f, 90.0f
	};

    void emit(float radius,RendererDayNightCycle.Presentation presentation,Sink sink)throws Exception {generate(radius,presentation,sink,false,false,false);}
    void emitAllGeometry(float radius,RendererDayNightCycle.Presentation presentation,Sink sink)throws Exception {generate(radius,presentation,sink,false,true,true);}
    int updatePalette(RendererDayNightCycle.Presentation presentation,Sink sink)throws Exception {generate(1,presentation,sink,true,true,false);return paletteCursor;}
    static float starVisibility(RendererDayNightCycle.Presentation p){float brightness=clampStatic((p.skyRed+p.skyGreen+p.skyBlue)/3.0f,0,1);return smoothSkyAmount((0.58f-brightness)/0.40f);}
    static float cloudVisibility(RendererDayNightCycle.Presentation p){float brightness=clampStatic((p.skyRed+p.skyGreen+p.skyBlue)/3.0f,0,1);return smoothSkyAmount((brightness-0.20f)/0.42f);}
    private void generate(float radius,RendererDayNightCycle.Presentation p,Sink sink,boolean colors,boolean all,boolean fullVisibility)throws Exception {
        if(!(radius>0)||!Float.isFinite(radius)||p==null||sink==null)throw new IllegalArgumentException("Finite positive sky radius and presentation/sink required");
        gl=sink;colorsOnly=colors;allLayers=all;paletteCursor=SKY_DOME_ELEVATION_DEGREES.length;
        try {
            gl.layer(0);drawWorldSkyDome(radius,p);
            float stars=fullVisibility?1:starVisibility(p),clouds=fullVisibility?1:cloudVisibility(p);
            gl.layer(1);drawWorldSkyMilkyWay(radius,stars);
            gl.layer(2);drawWorldSkyStars(radius,stars);
            gl.layer(3);drawWorldSkyClouds(radius,p,clouds);
        }finally{gl=null;}
    }
    private static float clampStatic(float value,float low,float high){return Math.max(low,Math.min(high,value));}
	private void drawWorldSkyClouds(
		float radius,
		RendererDayNightCycle.Presentation presentation,
		float visibility) throws Exception {
		if (!allLayers && visibility <= 0.001f) {
			return;
		}
		float red = mixSky(presentation.fogRed, 1.0f, 0.70f);
		float green = mixSky(presentation.fogGreen, 1.0f, 0.70f);
		float blue = mixSky(presentation.fogBlue, 1.0f, 0.74f);
		float alpha = 0.26f * visibility;
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		drawWorldSkyCloudCluster(radius, -18.0f, 8.0f, 1.00f, 0, red, green, blue, alpha);
		drawWorldSkyCloudCluster(radius, -34.0f, 42.0f, 0.82f, 1, red, green, blue, alpha * 0.74f);
		drawWorldSkyCloudCluster(radius, -51.0f, 75.0f, 0.76f, 2, red, green, blue, alpha * 0.66f);
		drawWorldSkyCloudCluster(radius, -22.0f, 108.0f, 1.12f, 0, red, green, blue, alpha * 0.94f);
		drawWorldSkyCloudCluster(radius, -39.0f, 143.0f, 0.91f, 1, red, green, blue, alpha * 0.72f);
		drawWorldSkyCloudCluster(radius, -17.0f, 177.0f, 0.86f, 2, red, green, blue, alpha * 0.80f);
		drawWorldSkyCloudCluster(radius, -46.0f, 210.0f, 0.80f, 0, red, green, blue, alpha * 0.70f);
		drawWorldSkyCloudCluster(radius, -27.0f, 241.0f, 1.04f, 1, red, green, blue, alpha * 0.86f);
		drawWorldSkyCloudCluster(radius, -15.0f, 274.0f, 1.08f, 0, red, green, blue, alpha * 0.90f);
		drawWorldSkyCloudCluster(radius, -43.0f, 303.0f, 0.74f, 2, red, green, blue, alpha * 0.68f);
		drawWorldSkyCloudCluster(radius, -25.0f, 334.0f, 0.94f, 1, red, green, blue, alpha * 0.78f);
		drawWorldSkyCloudCluster(radius, -58.0f, 356.0f, 0.68f, 2, red, green, blue, alpha * 0.58f);
		gl.glDisable(gl.GL_BLEND);
	}

	private void drawWorldSkyCloudCluster(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float scale,
		int variant,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		if (variant == 1) {
			drawWorldSkyWispyCloud(radius, elevationDegrees, azimuthDegrees, scale, red, green, blue, alpha);
			return;
		}
		if (variant == 2) {
			drawWorldSkyTowerCloud(radius, elevationDegrees, azimuthDegrees, scale, red, green, blue, alpha);
			return;
		}
		drawWorldSkyCumulusCloud(radius, elevationDegrees, azimuthDegrees, scale, red, green, blue, alpha);
	}

	private void drawWorldSkyCumulusCloud(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float scale,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.000f, 0.008f * scale, 0.082f * scale, 0.019f * scale,
			red, green, blue, alpha * 0.34f, 24);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.036f * scale, -0.003f * scale, 0.041f * scale, 0.026f * scale,
			red, green, blue, alpha * 0.58f, 20);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.009f * scale, -0.015f * scale, 0.054f * scale, 0.037f * scale,
			red, green, blue, alpha * 0.88f, 24);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.034f * scale, -0.007f * scale, 0.049f * scale, 0.030f * scale,
			red, green, blue, alpha * 0.72f, 22);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.012f * scale, -0.031f * scale, 0.036f * scale, 0.026f * scale,
			red, green, blue, alpha * 0.62f, 20);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.020f * scale, 0.012f * scale, 0.076f * scale, 0.015f * scale,
			red, green, blue, alpha * 0.25f, 24);
	}

	private void drawWorldSkyWispyCloud(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float scale,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.030f * scale, 0.000f, 0.092f * scale, 0.013f * scale,
			red, green, blue, alpha * 0.32f, 26);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.045f * scale, -0.009f * scale, 0.078f * scale, 0.012f * scale,
			red, green, blue, alpha * 0.28f, 24);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.056f * scale, -0.015f * scale, 0.048f * scale, 0.018f * scale,
			red, green, blue, alpha * 0.38f, 20);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.003f, -0.021f * scale, 0.050f * scale, 0.022f * scale,
			red, green, blue, alpha * 0.46f, 20);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.061f * scale, 0.008f * scale, 0.060f * scale, 0.010f * scale,
			red, green, blue, alpha * 0.22f, 22);
	}

	private void drawWorldSkyTowerCloud(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float scale,
		float red,
		float green,
		float blue,
		float alpha) throws Exception {
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.000f, 0.012f * scale, 0.076f * scale, 0.018f * scale,
			red, green, blue, alpha * 0.30f, 24);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.032f * scale, -0.004f * scale, 0.043f * scale, 0.027f * scale,
			red, green, blue, alpha * 0.58f, 20);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.022f * scale, -0.012f * scale, 0.051f * scale, 0.034f * scale,
			red, green, blue, alpha * 0.76f, 22);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.004f, -0.046f * scale, 0.040f * scale, 0.040f * scale,
			red, green, blue, alpha * 0.84f, 22);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			-0.010f * scale, -0.076f * scale, 0.027f * scale, 0.030f * scale,
			red, green, blue, alpha * 0.64f, 18);
		drawWorldSkySoftDisc(radius, elevationDegrees, azimuthDegrees,
			0.045f * scale, 0.004f * scale, 0.047f * scale, 0.022f * scale,
			red, green, blue, alpha * 0.46f, 20);
	}

	private void drawWorldSkyMilkyWay(float radius, float visibility) throws Exception {
		if (!allLayers && visibility <= 0.001f) {
			return;
		}
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		for (int patch = 0; patch < 18; patch++) {
			float azimuth = patch * 20.0f + 7.0f;
			double bandAngle = Math.toRadians(azimuth - 32.0f);
			float elevation = colorsOnly ? 0.0f : -43.0f + 23.0f * (float) Math.sin(bandAngle);
			float rotation = colorsOnly ? 0.0f : 19.0f * (float) Math.cos(bandAngle);
			drawWorldSkySoftDisc(radius, elevation, azimuth,
				0.0f, 0.0f, 0.145f, 0.026f,
				0.70f, 0.76f, 1.0f, visibility * 0.030f, 28, rotation);
			drawWorldSkySoftDisc(radius, elevation - 2.5f, azimuth + 4.0f,
				0.0f, 0.0f, 0.095f, 0.017f,
				0.92f, 0.88f, 1.0f, visibility * 0.022f, 24, rotation);
		}
		for (int star = 0; star < 180; star++) {
			float azimuth = (star * 71.317f + 13.0f) % 360.0f;
			double bandAngle = Math.toRadians(azimuth - 32.0f);
			float centerElevation = colorsOnly ? 0.0f : -43.0f + 23.0f * (float) Math.sin(bandAngle);
			float spread = (((star * 43 + 19) % 101) - 50) / 5.2f;
			float size = 0.00062f + ((star * 17 + 3) % 9) * 0.000055f;
			float alpha = visibility * (0.18f + ((star * 23 + 5) % 37) / 100.0f);
			drawWorldSkySoftDisc(radius, centerElevation + spread, azimuth,
				0.0f, 0.0f, size, size,
				0.78f, 0.84f, 1.0f, alpha, 10);
		}
		gl.glDisable(gl.GL_BLEND);
	}

	private void drawWorldSkyStars(float radius, float visibility) throws Exception {
		if (!allLayers && visibility <= 0.001f) {
			return;
		}
		gl.glEnable(gl.GL_BLEND);
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		for (int star = 0; star < 180; star++) {
			float azimuth = (star * 137.50777f + (star % 7) * 9.35f) % 360.0f;
			float elevation = -8.0f - ((star * 53 + 17) % 79);
			float brightness = 0.42f + ((star * 29 + 11) % 59) / 100.0f;
			float size = 0.00115f + ((star * 31 + 7) % 13) * 0.000075f;
			if (star % 17 == 0) {
				size *= 1.85f;
				brightness = 1.0f;
			}
			float red = star % 9 == 0 ? 1.0f : 0.88f;
			float green = star % 11 == 0 ? 0.84f : 0.93f;
			float blue = star % 9 == 0 ? 0.74f : 1.0f;
			drawWorldSkySoftDisc(radius, elevation, azimuth,
				0.0f, 0.0f, size, size, red, green, blue, visibility * brightness, 12);
		}
		gl.glDisable(gl.GL_BLEND);
	}

	private void drawWorldSkySoftDisc(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float tangentOffsetFraction,
		float verticalOffsetFraction,
		float tangentRadiusFraction,
		float verticalRadiusFraction,
		float red,
		float green,
		float blue,
		float alpha,
		int segments) throws Exception {
		drawWorldSkySoftDisc(
			radius,
			elevationDegrees,
			azimuthDegrees,
			tangentOffsetFraction,
			verticalOffsetFraction,
			tangentRadiusFraction,
			verticalRadiusFraction,
			red,
			green,
			blue,
			alpha,
			segments,
			0.0f);
	}

	private void drawWorldSkySoftDisc(
		float radius,
		float elevationDegrees,
		float azimuthDegrees,
		float tangentOffsetFraction,
		float verticalOffsetFraction,
		float tangentRadiusFraction,
		float verticalRadiusFraction,
		float red,
		float green,
		float blue,
		float alpha,
		int segments,
		float rotationDegrees) throws Exception {
        int centerPalette=paletteCursor++,edgePalette=paletteCursor++;
        if(colorsOnly){
            gl.glColor4f(centerPalette,red,green,blue,clampStatic(alpha,0.0f,1.0f));
            gl.glColor4f(edgePalette,red,green,blue,0.0f);
            return;
        }
		double elevation = Math.toRadians(elevationDegrees);
		double azimuth = Math.toRadians(azimuthDegrees);
		float cosElevation = (float) Math.cos(elevation);
		float sinElevation = (float) Math.sin(elevation);
		float sinAzimuth = (float) Math.sin(azimuth);
		float cosAzimuth = (float) Math.cos(azimuth);
		float directionX = cosElevation * sinAzimuth;
		float directionY = sinElevation;
		float directionZ = cosElevation * cosAzimuth;
		float centerRadius = radius * 0.965f;
		float tangentX = cosAzimuth;
		float tangentY = 0.0f;
		float tangentZ = -sinAzimuth;
		float verticalX = -sinElevation * sinAzimuth;
		float verticalY = cosElevation;
		float verticalZ = -sinElevation * cosAzimuth;
		if (rotationDegrees != 0.0f) {
			double rotation = Math.toRadians(rotationDegrees);
			float rotationCos = (float) Math.cos(rotation);
			float rotationSin = (float) Math.sin(rotation);
			float rotatedTangentX = tangentX * rotationCos + verticalX * rotationSin;
			float rotatedTangentY = tangentY * rotationCos + verticalY * rotationSin;
			float rotatedTangentZ = tangentZ * rotationCos + verticalZ * rotationSin;
			float rotatedVerticalX = verticalX * rotationCos - tangentX * rotationSin;
			float rotatedVerticalY = verticalY * rotationCos - tangentY * rotationSin;
			float rotatedVerticalZ = verticalZ * rotationCos - tangentZ * rotationSin;
			tangentX = rotatedTangentX;
			tangentY = rotatedTangentY;
			tangentZ = rotatedTangentZ;
			verticalX = rotatedVerticalX;
			verticalY = rotatedVerticalY;
			verticalZ = rotatedVerticalZ;
		}
		float tangentOffset = radius * tangentOffsetFraction;
		float verticalOffset = radius * verticalOffsetFraction;
		float centerX = directionX * centerRadius + tangentX * tangentOffset + verticalX * verticalOffset;
		float centerY = directionY * centerRadius + tangentY * tangentOffset + verticalY * verticalOffset;
		float centerZ = directionZ * centerRadius + tangentZ * tangentOffset + verticalZ * verticalOffset;
		float tangentRadius = radius * tangentRadiusFraction;
		float verticalRadius = radius * verticalRadiusFraction;

		gl.glBegin(gl.GL_TRIANGLES);
		for (int segment = 0; segment < segments; segment++) {
			gl.glColor4f(centerPalette, red, green, blue, clampStatic(alpha, 0.0f, 1.0f));
			drawWorldSkySoftDiscVertex(centerX, centerY, centerZ);
			gl.glColor4f(edgePalette, red, green, blue, 0.0f);
			drawWorldSkySoftDiscEdge(
				centerX,
				centerY,
				centerZ,
				tangentX,
				tangentY,
				tangentZ,
				verticalX,
				verticalY,
				verticalZ,
				tangentRadius,
				verticalRadius,
				segment * Math.PI * 2.0 / segments);
			drawWorldSkySoftDiscEdge(
				centerX,
				centerY,
				centerZ,
				tangentX,
				tangentY,
				tangentZ,
				verticalX,
				verticalY,
				verticalZ,
				tangentRadius,
				verticalRadius,
				(segment + 1) * Math.PI * 2.0 / segments);
		}
		gl.glEnd();
	}

	private void drawWorldSkySoftDiscEdge(
		float centerX,
		float centerY,
		float centerZ,
		float tangentX,
		float tangentY,
		float tangentZ,
		float verticalX,
		float verticalY,
		float verticalZ,
		float tangentRadius,
		float verticalRadius,
		double angle) throws Exception {
		float tangentAmount = tangentRadius * (float) Math.cos(angle);
		float verticalAmount = verticalRadius * (float) Math.sin(angle);
		drawWorldSkySoftDiscVertex(
			centerX + tangentX * tangentAmount + verticalX * verticalAmount,
			centerY + tangentY * tangentAmount + verticalY * verticalAmount,
			centerZ + tangentZ * tangentAmount + verticalZ * verticalAmount);
	}

	private void drawWorldSkySoftDiscVertex(float x, float y, float z) throws Exception {
		gl.glVertex3f(x, y, z);
	}

	private void drawWorldSkyDome(
		float radius,
		RendererDayNightCycle.Presentation presentation) throws Exception {
        if(colorsOnly){for(int ring=0;ring<SKY_DOME_ELEVATION_DEGREES.length;ring++)applyWorldSkyColor(presentation,SKY_DOME_ELEVATION_DEGREES[ring],ring);return;}
		gl.glBegin(gl.GL_QUADS);
		for (int ring = 0; ring + 1 < SKY_DOME_ELEVATION_DEGREES.length; ring++) {
			float upperElevation = SKY_DOME_ELEVATION_DEGREES[ring];
			float lowerElevation = SKY_DOME_ELEVATION_DEGREES[ring + 1];
			for (int segment = 0; segment < SKY_DOME_SEGMENTS; segment++) {
				float firstAzimuth = segment * 360.0f / SKY_DOME_SEGMENTS;
				float secondAzimuth = (segment + 1) * 360.0f / SKY_DOME_SEGMENTS;
				applyWorldSkyColor(presentation, upperElevation, ring);
				drawWorldSkyVertex(radius, upperElevation, firstAzimuth);
				drawWorldSkyVertex(radius, upperElevation, secondAzimuth);
				applyWorldSkyColor(presentation, lowerElevation, ring+1);
				drawWorldSkyVertex(radius, lowerElevation, secondAzimuth);
				drawWorldSkyVertex(radius, lowerElevation, firstAzimuth);
			}
		}
		gl.glEnd();
	}

	private void drawWorldSkyVertex(float radius, float elevationDegrees, float azimuthDegrees) throws Exception {
		double elevation = Math.toRadians(elevationDegrees);
		double azimuth = Math.toRadians(azimuthDegrees);
		float horizontalRadius = radius * (float) Math.cos(elevation);
		gl.glVertex3f(
			horizontalRadius * (float) Math.sin(azimuth),
			radius * (float) Math.sin(elevation),
			horizontalRadius * (float) Math.cos(azimuth));
	}

	private void applyWorldSkyColor(
		RendererDayNightCycle.Presentation presentation,
		float elevationDegrees, int palette) throws Exception {
		float horizonRed = presentation.fogRed;
		float horizonGreen = presentation.fogGreen;
		float horizonBlue = presentation.fogBlue;
		if (elevationDegrees >= 0.0f) {
			float below = smoothSkyAmount(elevationDegrees / 90.0f);
			gl.glColor4f(palette,
				mixSky(horizonRed, horizonRed * 0.34f, below),
				mixSky(horizonGreen, horizonGreen * 0.36f, below),
				mixSky(horizonBlue, horizonBlue * 0.44f, below),
				1.0f);
			return;
		}

		float altitude = smoothSkyAmount(-elevationDegrees / 90.0f);
		float zenithRed = clampStatic(presentation.skyRed * 0.58f, 0.0f, 1.0f);
		float zenithGreen = clampStatic(presentation.skyGreen * 0.70f, 0.0f, 1.0f);
		float zenithBlue = clampStatic(presentation.skyBlue * 1.08f, 0.0f, 1.0f);
		gl.glColor4f(palette,
			mixSky(horizonRed, zenithRed, altitude),
			mixSky(horizonGreen, zenithGreen, altitude),
			mixSky(horizonBlue, zenithBlue, altitude),
			1.0f);
	}

	private static float smoothSkyAmount(float value) {
		float clamped = clampStatic(value, 0.0f, 1.0f);
		return clamped * clamped * (3.0f - 2.0f * clamped);
	}

	private static float mixSky(float from, float to, float amount) {
		return from + (to - from) * clampStatic(amount, 0.0f, 1.0f);
	}

}
