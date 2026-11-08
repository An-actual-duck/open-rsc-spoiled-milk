package orsc;

import java.nio.FloatBuffer;

/** Stable directional-light projection in true world coordinates (Y down). */
final class DirectionalShadowProjection {
	final float[] matrix = new float[16]; // column-major, as consumed by GLSL
	final double centerX, centerY, centerZ;
    final double gridCenterX, gridCenterY, gridCenterZ;
	final float worldTexel, halfExtent, depthExtent;
	final float lightX, lightY, lightZ;

	DirectionalShadowProjection(double x, double y, double z,
		float sunX, float sunY, float sunZ, float half, int size) {
        this(x,y,z,sunX,sunY,sunZ,half,size,null,false,y);
    }

    /**
     * Track the near grid in world space. Snapping the absolute light-space
     * center again as the sun rotates moves the texel phase across the scene,
     * even with a stationary camera (and more often at large map coordinates).
     * Only camera displacement is quantized here; caster geometry stays fresh.
     */
    DirectionalShadowProjection(double x, double y, double z,
        float sunX, float sunY, float sunZ, float half, int size, DirectionalShadowProjection previous) {
        this(x,y,z,sunX,sunY,sunZ,half,size,previous,true,y);
    }

    DirectionalShadowProjection(double x, double y, double z,
        float sunX, float sunY, float sunZ, float half, int size,
        DirectionalShadowProjection previous, double referenceHeight) {
        this(x,y,z,sunX,sunY,sunZ,half,size,previous,true,referenceHeight);
    }

    private DirectionalShadowProjection(double x, double y, double z,
        float sunX, float sunY, float sunZ, float half, int size, DirectionalShadowProjection previous, boolean tracking, double referenceHeight) {
		if (!(half > 0) || !Float.isFinite(half) || size <= 0
			|| !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
			|| !Float.isFinite(sunX) || !Float.isFinite(sunY) || !Float.isFinite(sunZ)) {
			throw new IllegalArgumentException("Finite light/center and positive extent/resolution required");
		}
		double length = Math.sqrt((double) sunX * sunX + (double) sunY * sunY + (double) sunZ * sunZ);
		if (length < 0.000001) throw new IllegalArgumentException("Zero sun direction");
		lightX = (float) (sunX / length);
		lightY = (float) (-sunY / length); // lighting uses Y-up; geometry is Y-down
		lightZ = (float) (sunZ / length);
		// Double-normalized tracking basis avoids accumulated anchor drift.
        double fx = tracking ? -sunX/length : -lightX;
        double fy = tracking ? sunY/length : -lightY;
        double fz = tracking ? -sunZ/length : -lightZ;
		double rx, ry, rz;
		if (Math.abs(fy) > 0.99) { rx = 0; ry = -fz; rz = fy; }
		else { rx = -fz; ry = 0; rz = fx; }
		double rLength = Math.sqrt(rx * rx + ry * ry + rz * rz);
		rx /= rLength; ry /= rLength; rz /= rLength;
		double ux = fy * rz - fz * ry;
		double uy = fz * rx - fx * rz;
		double uz = fx * ry - fy * rx;
		centerX = x; centerY = y; centerZ = z;
		halfExtent = half;
		depthExtent = Math.max(8192.0f, half * 3.0f);
		worldTexel = half * 2.0f / size;
		if (!Float.isFinite(depthExtent) || !Float.isFinite(worldTexel) || worldTexel <= 0) {
			throw new IllegalArgumentException("Shadow extent exceeds float projection precision");
		}
        double cx, cy;
        double cz = fx * x + fy * y + fz * z;
        double gx=x, gy=y, gz=z;
        if (!tracking) {
            cx = Math.rint((rx * x + ry * y + rz * z) / worldTexel) * worldTexel;
            cy = Math.rint((ux * x + uy * y + uz * z) / worldTexel) * worldTexel;
        } else if (Math.abs(sunY) > 0.0001f && Double.isFinite(referenceHeight)) {
            // World-ground-aligned texels avoid rotating a raster grid through
            // repeated cutouts. These rows are constant along each sun ray.
            double shearX=(double)sunX/sunY, shearZ=(double)sunZ/sunY;
            rx=1;ry=shearX;rz=0;ux=0;uy=shearZ;uz=1;
            gy=referenceHeight;
            if (previous != null) {
                double heightDelta=gy-previous.gridCenterY;
                double stepX=Math.rint((x-previous.gridCenterX+shearX*heightDelta)/worldTexel)*worldTexel;
                double stepZ=Math.rint((z-previous.gridCenterZ+shearZ*heightDelta)/worldTexel)*worldTexel;
                gx=previous.gridCenterX+stepX-shearX*heightDelta;
                gz=previous.gridCenterZ+stepZ-shearZ*heightDelta;
            }
            cx=gx+shearX*gy;cy=gz+shearZ*gy;
        } else {
            cx=rx*x+ry*y+rz*z;cy=ux*x+uy*y+uz*z;
        }
        gridCenterX=gx; gridCenterY=gy; gridCenterZ=gz;
		row(0, rx, ry, rz, cx, half);
		row(1, ux, uy, uz, cy, half);
		row(2, fx, fy, fz, cz, depthExtent);
		matrix[15] = 1;
	}

	private void row(int row, double x, double y, double z, double middle, double extent) {
		matrix[row] = (float) (x / extent);
		matrix[4 + row] = (float) (y / extent);
		matrix[8 + row] = (float) (z / extent);
		matrix[12 + row] = (float) (-middle / extent);
	}

	FloatBuffer put(FloatBuffer buffer) {
		buffer.clear(); buffer.put(matrix); buffer.flip(); return buffer;
	}

	/** Light-frustum culling, deliberately independent of the camera frustum. */
	boolean intersects(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		for (int row = 0; row < 3; row++) {
			float cx = (minX + maxX) * .5f, cy = (minY + maxY) * .5f, cz = (minZ + maxZ) * .5f;
			float middle = matrix[row] * cx + matrix[row + 4] * cy + matrix[row + 8] * cz + matrix[row + 12];
			float radius = Math.abs(matrix[row]) * (maxX - minX) * .5f
				+ Math.abs(matrix[row + 4]) * (maxY - minY) * .5f
				+ Math.abs(matrix[row + 8]) * (maxZ - minZ) * .5f;
			if (middle - radius > 1 || middle + radius < -1) return false;
		}
		return true;
	}
}
