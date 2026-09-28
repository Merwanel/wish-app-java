import sharp from "sharp";

export async function resizeAndConvert(imageBuffer: Uint8Array): Promise<Uint8Array> {
	if (imageBuffer.length === 0) {
		return new Uint8Array();
	}
	const buffer = await sharp(imageBuffer)
		.resize(200, 300, {
			position: sharp.strategy.attention
		})
		.webp({ quality: 60 })
		.toBuffer();

	return new Uint8Array(buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength));
}
