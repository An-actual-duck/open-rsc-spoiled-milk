package orsc.graphics.three;

/** Allocation-free ray/face checks used only by invisible interaction footprints. */
final class InteractionPickGeometry {
    private InteractionPickGeometry() { }

    static double depth(RSModel model, int face, double rayX, double rayY) {
        int[] vertices = model.faceIndices[face];
        if (vertices.length < 3) return Double.NaN;
        int a = vertices[0], b = vertices[1], c = vertices[vertices.length - 1];
        double ux = model.vertXRot[b] - model.vertXRot[a];
        double uy = model.vertYRot[b] - model.vertYRot[a];
        double uz = model.vertZRot[b] - model.vertZRot[a];
        double vx = model.vertXRot[c] - model.vertXRot[a];
        double vy = model.vertYRot[c] - model.vertYRot[a];
        double vz = model.vertZRot[c] - model.vertZRot[a];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double denominator = nx * rayX + ny * rayY + nz;
        if (Math.abs(denominator) < 0.000001D) return Double.NaN;
        double result = (nx * model.vertXRot[a] + ny * model.vertYRot[a] + nz * model.vertZRot[a]) / denominator;
        return result > 0.0D && Double.isFinite(result) ? result : Double.NaN;
    }

    /** Same planar U/V basis and repeating, nearest texel convention as the world renderer. */
    static boolean opaqueAt(RSModel model, int face, int material, double depth,
                            double rayX, double rayY, Renderer3DTextureData[] textures) {
        if (material == Scene.TRANSPARENT || model.isInteractionOnly()) return false;
        if (material < 0) return true;
        if (material >= textures.length) material = 0;
        Renderer3DTextureData texture = textures.length == 0 ? null : textures[material];
        if (texture == null || !texture.hasTransparency()) return true;
        int[] vertices = model.faceIndices[face];
        int a = vertices[0], b = vertices[1], c = vertices[vertices.length - 1];
        double ux = model.vertXRot[b] - model.vertXRot[a], uy = model.vertYRot[b] - model.vertYRot[a], uz = model.vertZRot[b] - model.vertZRot[a];
        double vx = model.vertXRot[c] - model.vertXRot[a], vy = model.vertYRot[c] - model.vertYRot[a], vz = model.vertZRot[c] - model.vertZRot[a];
        double px = rayX * depth - model.vertXRot[a], py = rayY * depth - model.vertYRot[a], pz = depth - model.vertZRot[a];
        double uu = ux * ux + uy * uy + uz * uz, uv = ux * vx + uy * vy + uz * vz, vv = vx * vx + vy * vy + vz * vz;
        double determinant = uu * vv - uv * uv;
        if (Math.abs(determinant) < 0.000001D) return true;
        double pu = px * ux + py * uy + pz * uz, pv = px * vx + py * vy + pz * vz;
        double u = model.getFaceTextureUStart(face) + (pu * vv - pv * uv) / determinant * model.getFaceTextureUScale(face);
        double v = (pv * uu - pu * uv) / determinant;
        int x = Math.floorMod((int) Math.floor(u * texture.getWidth()), texture.getWidth());
        int y = Math.floorMod((int) Math.floor(v * texture.getHeight()), texture.getHeight());
        return (texture.getRgbaPixels()[y * texture.getWidth() + x] & 255) != 0;
    }
}
