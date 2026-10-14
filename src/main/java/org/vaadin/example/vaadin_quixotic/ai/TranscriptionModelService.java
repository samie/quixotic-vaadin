package org.vaadin.example.vaadin_quixotic.ai;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import com.qxotic.jinfer.spring.ai.JinferTranscriptionModel;

@Service
public class TranscriptionModelService extends AbstractModelService<JinferTranscriptionModel> {

	private static final int WARMUP_SAMPLE_RATE = 16_000;
	private static final int WARMUP_SAMPLES = WARMUP_SAMPLE_RATE / 4;

	public TranscriptionModelService(@Value("${app.transcription.model:}") String modelRef) {
		super(modelRef);
	}

	@Override
	protected JinferTranscriptionModel buildModel() {
		return JinferTranscriptionModel.builder().model(modelRef()).build();
	}

	@Override
	protected void warmUp(JinferTranscriptionModel model) {
		ByteArrayResource clip = new ByteArrayResource(silentWav(WARMUP_SAMPLE_RATE, WARMUP_SAMPLES)) {
			@Override
			public String getFilename() {
				return "warmup.wav";
			}
		};
		model.transcribe(clip);
	}

	private static byte[] silentWav(int sampleRate, int samples) {
		int dataBytes = samples * 2;
		ByteBuffer buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
		buf.put("RIFF".getBytes());
		buf.putInt(36 + dataBytes);
		buf.put("WAVE".getBytes());
		buf.put("fmt ".getBytes());
		buf.putInt(16);
		buf.putShort((short) 1);
		buf.putShort((short) 1);
		buf.putInt(sampleRate);
		buf.putInt(sampleRate * 2);
		buf.putShort((short) 2);
		buf.putShort((short) 16);
		buf.put("data".getBytes());
		buf.putInt(dataBytes);
		return buf.array();
	}
}
