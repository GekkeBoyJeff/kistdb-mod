package nl.libreria.claims;

import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Any straight-line stretch, shear or turn of a display entity's model, in the four parts a display entity
 * understands: translation · left rotation · scale · right rotation. With it one flat square model can be bent onto
 * any triangle or slanted strip, which is how the land map lays a smooth skin over hills.
 */
final class Affine {
	final Vector3f translation;
	final Quaternionf left;
	final Vector3f scale;
	final Quaternionf right;

	private Affine(Vector3f translation, Quaternionf left, Vector3f scale, Quaternionf right) {
		this.translation = translation;
		this.left = left;
		this.scale = scale;
		this.right = right;
	}

	/**
	 * The map that takes the model points {@code a, b, c} and the model direction {@code n} onto {@code A, B, C} and
	 * {@code N} (a, b, c, n must not lie in one plane). If it would mirror the model, {@code N} is turned around,
	 * which only moves a flat model's (invisible) thickness to its other side.
	 */
	static Affine fit(Vector3f a, Vector3f b, Vector3f c, Vector3f n, Vector3f A, Vector3f B, Vector3f C, Vector3f N) {
		Matrix3f model = new Matrix3f(new Vector3f(b).sub(a), n, new Vector3f(c).sub(a)).invert();
		Matrix3f world = new Matrix3f(new Vector3f(B).sub(A), N, new Vector3f(C).sub(A));
		Matrix3f m = new Matrix3f(world).mul(model);
		if (m.determinant() < 0) {
			world = new Matrix3f(new Vector3f(B).sub(A), new Vector3f(N).negate(), new Vector3f(C).sub(A));
			m = new Matrix3f(world).mul(model);
		}
		Vector3f t = new Vector3f(A).sub(m.transform(new Vector3f(a)));
		return decompose(m, t);
	}

	/** The same shape shrunk by {@code k} towards the model point {@code anchor} (which stays where it is). */
	Affine scaledAbout(Vector3f anchor, float k) {
		Vector3f s = new Vector3f(this.scale).mul(k);
		Vector3f fixed = apply(this.translation, this.scale, anchor);
		Vector3f moved = apply(new Vector3f(), s, anchor);
		return new Affine(fixed.sub(moved), new Quaternionf(this.left), s, new Quaternionf(this.right));
	}

	private Vector3f apply(Vector3f t, Vector3f s, Vector3f p) {
		Vector3f v = this.right.transform(new Vector3f(p));
		v.mul(s);
		this.left.transform(v);
		return v.add(t);
	}

	void applyTo(DisplayElement el) {
		el.setTranslation(new Vector3f(this.translation));
		el.setLeftRotation(new Quaternionf(this.left));
		el.setScale(new Vector3f(this.scale));
		el.setRightRotation(new Quaternionf(this.right));
	}

	/**
	 * m = L · diag(s) · R with L and R rotations (a singular value decomposition), found with Jacobi sweeps on
	 * mᵀm. A mirrored m ends up as a negative last scale.
	 */
	private static Affine decompose(Matrix3f m, Vector3f t) {
		double[][] a = new double[3][3];
		double[][] mm = new double[3][3];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) mm[i][j] = m.get(j, i); // row i, column j
		}
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				double sum = 0;
				for (int k = 0; k < 3; k++) sum += mm[k][i] * mm[k][j];
				a[i][j] = sum;
			}
		}
		double[][] v = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
		for (int sweep = 0; sweep < 24; sweep++) {
			double off = a[0][1] * a[0][1] + a[0][2] * a[0][2] + a[1][2] * a[1][2];
			if (off < 1e-20) break;
			for (int p = 0; p < 2; p++) {
				for (int q = p + 1; q < 3; q++) {
					if (Math.abs(a[p][q]) < 1e-15) continue;
					double theta = (a[q][q] - a[p][p]) / (2 * a[p][q]);
					double tt = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
					if (theta == 0) tt = 1;
					double cs = 1 / Math.sqrt(tt * tt + 1), sn = tt * cs;
					// a = Jᵀ a J, v = v J, with J the rotation in the (p, q) plane
					for (int k = 0; k < 3; k++) {
						double akp = a[k][p], akq = a[k][q];
						a[k][p] = cs * akp - sn * akq;
						a[k][q] = sn * akp + cs * akq;
					}
					for (int k = 0; k < 3; k++) {
						double apk = a[p][k], aqk = a[q][k];
						a[p][k] = cs * apk - sn * aqk;
						a[q][k] = sn * apk + cs * aqk;
					}
					for (int k = 0; k < 3; k++) {
						double vkp = v[k][p], vkq = v[k][q];
						v[k][p] = cs * vkp - sn * vkq;
						v[k][q] = sn * vkp + cs * vkq;
					}
				}
			}
		}
		Vector3f v0 = new Vector3f((float) v[0][0], (float) v[1][0], (float) v[2][0]).normalize();
		Vector3f v1 = new Vector3f((float) v[0][1], (float) v[1][1], (float) v[2][1]);
		v1.sub(new Vector3f(v0).mul(v0.dot(v1))).normalize();
		Vector3f v2 = new Vector3f(v0).cross(v1).normalize();
		Vector3f u0 = m.transform(new Vector3f(v0));
		Vector3f u1 = m.transform(new Vector3f(v1));
		u0 = u0.lengthSquared() < 1e-12f ? perpendicular(v0) : u0.normalize();
		u1.sub(new Vector3f(u0).mul(u0.dot(u1)));
		u1 = u1.lengthSquared() < 1e-12f ? new Vector3f(u0).cross(v2).normalize() : u1.normalize();
		Vector3f u2 = new Vector3f(u0).cross(u1).normalize();
		Vector3f s = new Vector3f(u0.dot(m.transform(new Vector3f(v0))), u1.dot(m.transform(new Vector3f(v1))),
				u2.dot(m.transform(new Vector3f(v2))));
		Quaternionf left = new Quaternionf().setFromNormalized(new Matrix3f(u0, u1, u2));
		// R = Vᵀ: the rotation whose rows are v0, v1, v2
		Quaternionf right = new Quaternionf().setFromNormalized(new Matrix3f(v0, v1, v2).transpose());
		return new Affine(t, left, s, right);
	}

	private static Vector3f perpendicular(Vector3f v) {
		Vector3f other = Math.abs(v.x) < 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		return new Vector3f(v).cross(other).normalize();
	}
}
