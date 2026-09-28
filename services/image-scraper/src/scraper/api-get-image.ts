import { promises as fs } from 'fs';
import { Browser, Page } from 'playwright';
import { SearchLocators } from './search_const';


const { chromium } = require('playwright-extra')
const stealth = require('puppeteer-extra-plugin-stealth')()
chromium.use(stealth)


const MAX_RETRY = 5;

export class LocatorFunctions {
	page!: Page;
	search_locators!: SearchLocators;
	constructor({ page, search_locators }: { page: Page, search_locators: SearchLocators }) {
		this.search_locators = search_locators;
		this.page = page;
	}
	async search(searchTerm: string) {
		await this.page.goto(
			`${this.search_locators.search_Url}${encodeURIComponent(searchTerm)}%20big%20cover${this.search_locators.param_search}`,
			{ waitUntil: 'domcontentloaded' }
		);
		if (this.search_locators.have_cookie_question) {
			// supposes the page is in english
			const rejectAllLink = this.page.getByRole('button', { name: 'Reject all' });
			rejectAllLink.waitFor({ timeout: 3000 });
			rejectAllLink.click();
		}
	}
	async waitFirstImage() {
		await this.page.waitForSelector(this.search_locators.locatorSmallImage, { timeout: 3000 });
	}
	locateSmallImageLink() {
		return this.page.locator(this.search_locators.locatorSmallImage).first();
	}
	locateBigImageLink() {
		return this.search_locators.locatorBigImage(this.page);
	}


}

/**
 * Usage : 
 * const fetcher = await ImageFetcher.create();
*/
export class ImageFetcher {
	// "async constructor" not allowed
	constructor(private browser: Browser, public page: Page) { }

	static async create() {
		const browser = await chromium.launch({
			headless: true,
			timeout: 10000,
			args: ['--no-sandbox', '--disable-setuid-sandbox']
		});

		const page = await browser.newPage();

		page.setDefaultTimeout(30000);
		page.setDefaultNavigationTimeout(30000);

		page.on('crash', () => {
			console.error('Page crashed during operation');
		});

		return new ImageFetcher(browser, page);
	}

	getNumberAround(mid: number) {
		const sign_is_positive = Math.random() > .5;
		let factor = 0.3 * Math.random();
		if (!sign_is_positive) {
			factor = -factor;
		}
		const result = mid + mid * factor;
		return Math.max(100, result);
	}
	async retryBlock(remaining_try: number, f: () => Promise<void>): Promise<number> {
		let has_failed = true;
		while (remaining_try > 0 && has_failed) {
			has_failed = false;
			try {
				await f();
			}
			catch (e) {
				has_failed = true;
				remaining_try--;
				if (remaining_try == 0) {
					throw (e);
				}
			}
		}

		return remaining_try;

	}

	async downloadImage(locatorFunctions: LocatorFunctions, searchTerm: string) {
		let result: Uint8Array | undefined;

		try {
			let remaining_try = MAX_RETRY;
			await locatorFunctions.search(searchTerm);
			remaining_try = await this.retryBlock(remaining_try, async () => {
				await locatorFunctions.waitFirstImage()
			})
			const firstImageLink = locatorFunctions.locateSmallImageLink();

			if (await firstImageLink.isVisible()) {
				await this.page.waitForTimeout(this.getNumberAround(500));
				await firstImageLink.click();
				let imageUrl: string | null = "";
				remaining_try = await this.retryBlock(remaining_try, async () => {
					await this.page.waitForTimeout(this.getNumberAround(400));
					const imageLocator = locatorFunctions.locateBigImageLink();
					await imageLocator.waitFor({ state: 'attached', timeout: 3000 });
					imageUrl = await imageLocator.getAttribute('src');
				})

				try {
					const response = await this.page.request.get(locatorFunctions.search_locators.prefixDDL + imageUrl);
					const buffer = new Uint8Array(await response.body());
					result = buffer;
					return buffer;
				} catch (e) {
					console.error(locatorFunctions.search_locators.name + ' Could not find a valid image URL for download.', e);
				}
			} else {
				console.error(locatorFunctions.search_locators.name + ' No images found on the search results page.');
			}

		} catch (error) {
			console.error(locatorFunctions.search_locators.name + ' An error occurred:', error);

			if (!this.browser.isConnected()) {
				console.error('Browser is no longer connected, skipping screenshot');
			} else {
				// screenshot + dump page HTML for debugging
				try {
					const screenshot_path = `/tmp/${locatorFunctions.search_locators.name}_error_screenshot.png`;
					await this.page.screenshot({ path: screenshot_path, timeout: 5000 });
					console.error(locatorFunctions.search_locators.name + ' Screenshot saved at:', screenshot_path);
				} catch (screenshotError) {
					console.error('Screenshot failed:', screenshotError instanceof Error ? screenshotError.message : screenshotError);
				}
				try {
					const html_path = `/tmp/${locatorFunctions.search_locators.name}_error.html`;
					const html = await this.page.content();
					await fs.writeFile(html_path, html, 'utf-8');
					console.error(locatorFunctions.search_locators.name + ' HTML saved at:', html_path);
				} catch (htmlError) {
					console.error('HTML dump failed:', htmlError instanceof Error ? htmlError.message : htmlError);
				}
			}
		} finally {
			try {
				if (this.browser.isConnected()) {
					await this.browser.close();
				}
			} catch (closeError) {
				console.error('Error closing browser:', closeError instanceof Error ? closeError.message : closeError);
			}
		}

		return result;
	}
}