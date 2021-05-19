package com.jozufozu.flywheel.backend.gl.shader;

import com.jozufozu.flywheel.backend.ShaderContext;
import com.jozufozu.flywheel.backend.ShaderLoader;
import com.jozufozu.flywheel.backend.loading.Program;

public class SingleProgram<P extends GlProgram> implements IMultiProgram<P> {
	final P program;

	public SingleProgram(P program) {
		this.program = program;
	}

	@Override
	public P get() {
		return program;
	}

	@Override
	public void delete() {
		program.delete();
	}

	public static class SpecLoader<P extends GlProgram> implements ShaderSpecLoader<P> {
		final ProgramFactory<P> factory;

		public SpecLoader(ProgramFactory<P> factory) {
			this.factory = factory;
		}

		@Override
		public IMultiProgram<P> create(ShaderLoader loader, ShaderContext<P> ctx, ProgramSpec spec) {
			return new SingleProgram<>(factory.create(ctx.loadProgram(loader, spec, null)));
		}
	}

	@FunctionalInterface
	public interface ProgramFactory<P extends GlProgram> {
		P create(Program program);
	}
}