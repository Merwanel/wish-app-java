import { parentPort } from "worker_threads";
import { resizeAndConvert } from "../image-processing";
import { ImageFetcher, LocatorFunctions } from "./api-get-image";
import { SearchLocators, SerializableSearchLocators, getLocatorBigImage } from "./search_const";

if (parentPort) {
	parentPort.on('message', async ({ search_locators, search_term }: { search_locators: SerializableSearchLocators; search_term: string }) => {
		try {
			const imageFetcher = await ImageFetcher.create();

			const fullSearchLocators: SearchLocators = {
				...search_locators,
				locatorBigImage: (page: any) => getLocatorBigImage(search_locators.locatorBigImage, page)
			};

			const locatorFunctions = new LocatorFunctions({
				page: imageFetcher.page,
				search_locators: fullSearchLocators
			});
			const buffer = await imageFetcher.downloadImage(locatorFunctions, search_term);
			let converted: Uint8Array = new Uint8Array();
			if (buffer && buffer.length > 0) {
				converted = await resizeAndConvert(buffer);
			}

			parentPort?.postMessage(converted);
		} catch (err: any) {
			console.error(`Worker error for ${search_locators?.name}:`, err);
			parentPort?.postMessage(new Uint8Array());
		}
	});
}
