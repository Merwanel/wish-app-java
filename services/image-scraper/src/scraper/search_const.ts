import { Locator, Page } from "playwright";

export interface SearchLocators {
    name:string,
    search_Url:string,
    param_search:string,
    have_cookie_question?:boolean,
    locatorSmallImage:string,
    locatorBigImage: (page: Page) => Locator,
    prefixDDL: string
};

export interface SerializableSearchLocators {
    name:string,
    search_Url:string,
    param_search:string,
    have_cookie_question?:boolean,
    locatorSmallImage:string,
    locatorBigImage: string, // a function is not natively serializable
    prefixDDL: string
};
export const SEARCH_ENGINE : SearchLocators[] = [
    {
        name:'GOOGLE',
        search_Url: "https://www.google.com/search?tbm=isch&q=",
        param_search: "&hl=en&gl=us&tbs=isz:l",
        have_cookie_question:true,
        locatorSmallImage: "div#search g-img img",
        locatorBigImage:  (page:Page) => page.locator("div[role='dialog']").last().locator("img").nth(1),
        prefixDDL: ""
    },
    {
        name:'START_PAGE',
        search_Url: "https://www.startpage.com/do/asearch?query=",
        param_search:"&cat=images",
        locatorSmallImage: "div.image-quick-details",
        locatorBigImage: (page:Page) => page.locator("#expanded-image").first(),
        prefixDDL: "https://www.startpage.com"
    },
    {
        name:'BING',
        search_Url:"https://www.bing.com/images/search?q=",
        param_search:"?filterui:imagesize-medium",
        locatorSmallImage:".imgpt",
        locatorBigImage: (page:Page) => page.locator('iframe').first().contentFrame().locator("img.nofocus").first(),
        prefixDDL: ""
    },
    {
        name:'BRAVE',
        search_Url:"https://search.brave.com/images?q=",
        param_search:"",
        locatorSmallImage:".image-result img",
        locatorBigImage: (page:Page) => page.locator("a.images-selected-image-wrapper img").first(),
        prefixDDL: ""
    },
];

export const SERIALIZABLE_SEARCH_ENGINE: SerializableSearchLocators[] =
    SEARCH_ENGINE.map((search_locator) => {
        return {...search_locator, locatorBigImage:search_locator.name}
    })

export function getLocatorBigImage(type: string, page: Page): Locator {
    const locatorBigImage = SEARCH_ENGINE.find((search_locator) => search_locator.name === type)
    if(!locatorBigImage) {
        throw new Error(`Unknown locator type: ${type}`);
    }
    return locatorBigImage.locatorBigImage(page) ;
}