import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_DB_URL } from '../const';
import { map, switchMap, from, Observable } from 'rxjs';
import {
  convertBase64ToUint8ArrayAndAddUUIDToTagsAndAddLetters,
  convertBase64ToUint8ArrayAndAddUUIDToTagsAndAddLettersArray,
  convertUint8ArrayToBase64,
  convertUint8ArrayToBase64Partial,
  WishOmitId,
  WishPartial,
  WishWRate,
} from '../schemas/wish.schema';
import { TagsService } from './tags.service';
import { base64ToUint8Array, uint8ArrayToBase64 } from 'uint8array-extras';
import { z } from 'zod';
import { WishDTO } from 'shared-schemas';

const WishSearchHitSchema = WishDTO.extend({
  highlightedName: z.string().nullable().optional(),
  highlightedComment: z.string().nullable().optional(),
});

const WishSearchResponseSchema = z.object({
  total: z.number(),
  wishes: z.array(WishSearchHitSchema),
  aggregations: z.object({
    tags: z.array(z.object({
      key: z.string(),
      count: z.number(),
    })),
  }),
});

export type TagFacet = { key: string; count: number };

@Injectable({
  providedIn: 'root',
})
export class WishService {
  constructor(private http: HttpClient) {}

  private wishes: WishWRate[] = [];
  private searchQuery = '';
  private selectedTag: string | null = null;
  private total = 0;
  private tagFacets: TagFacet[] = [];
  private headers = new HttpHeaders({ 'Content-Type': 'application/json' });
  private tagsService?: TagsService;

  get getWishes() {
    return this.wishes;
  }
  set setWishes(wishes: WishWRate[]) {
    this.wishes = wishes;
  }
  setWish(wish: WishWRate, idx: number) {
    this.wishes[idx] = wish;
  }
  forceDetectChange() {
    this.wishes = [...this.getWishes];
  }

  get getSearchQuery() {
    return this.searchQuery;
  }
  get getSelectedTag() {
    return this.selectedTag;
  }
  get getTotal() {
    return this.total;
  }
  get getTagFacets() {
    return this.tagFacets;
  }

  /** @deprecated Prefer getSearchQuery; kept for transitional URL join usage */
  get getSearchWords() {
    return this.searchQuery.trim() ? this.searchQuery.trim().split(/\s+/) : [];
  }

  /**
   * Browse / search via GET /wishes/search (A2). Empty q = browse (match_all).
   */
  searchWishes(options: {
    q?: string;
    tag?: string | null;
    limit?: number;
    offset?: number;
    tagsService?: TagsService;
  } = {}): Promise<void> {
    if (options.tagsService) {
      this.tagsService = options.tagsService;
    }
    this.searchQuery = options.q ?? '';
    this.selectedTag = options.tag ?? null;

    let params = new HttpParams()
      .set('limit', String(options.limit ?? 50))
      .set('offset', String(options.offset ?? 0));
    if (this.searchQuery.trim()) {
      params = params.set('q', this.searchQuery.trim());
    }
    if (this.selectedTag) {
      params = params.set('tag', this.selectedTag);
    }

    return new Promise<void>((resolve, reject) => {
      this.http
        .get(`${API_DB_URL}wishes/search`, { params })
        .pipe(map((body) => WishSearchResponseSchema.parse(body)))
        .subscribe({
          next: (response) => {
            this.total = response.total;
            this.tagFacets = response.aggregations.tags;
            this.wishes = convertBase64ToUint8ArrayAndAddUUIDToTagsAndAddLettersArray(
              response.wishes
            ).map((wish, i) => {
              const hit = response.wishes[i];
              return {
                ...wish,
                track_id: new Date().toISOString() + wish.id,
                highlightedName: hit.highlightedName ?? null,
                highlightedComment: hit.highlightedComment ?? null,
              };
            });
            const tagsServiceToUse = options.tagsService || this.tagsService;
            if (tagsServiceToUse) {
              tagsServiceToUse.setTagsFromFacets(this.tagFacets.map((t) => t.key));
            }
            resolve();
          },
          error: (err) => reject(err),
        });
    });
  }

  /** Alias used by components that previously called fetchWishes after mutations. */
  fetchWishes(tagsService?: TagsService, paging?: { limit?: number; offset?: number }): Promise<void> {
    return this.searchWishes({
      q: this.searchQuery,
      tag: this.selectedTag,
      limit: paging?.limit ?? 50,
      offset: paging?.offset ?? 0,
      tagsService,
    });
  }

  addWish(wishData: WishOmitId) {
    const WishDataToSend = convertUint8ArrayToBase64(wishData);
    return this.http
      .post(`${API_DB_URL}new-wish`, JSON.stringify(WishDataToSend), { headers: this.headers })
      .pipe(
        switchMap((res) => {
          return from(this.fetchWishes()).pipe(map(() => res));
        })
      );
  }

  updateWish(wishData: WishPartial) {
    const WishDataToSend = convertUint8ArrayToBase64Partial(wishData);
    const req = this.http
      .patch(`${API_DB_URL}update-wish`, JSON.stringify(WishDataToSend), { headers: this.headers })
      .pipe(map((res) => WishDTO.parse(res)))
      .pipe(map((res) => convertBase64ToUint8ArrayAndAddUUIDToTagsAndAddLetters(res)))
      .pipe<WishWRate>(
        map((wish) => ({
          ...wish,
          track_id: new Date().toISOString() + wish.id,
          highlightedName: null,
          highlightedComment: null,
        }))
      );
    req.subscribe(() => {
      this.fetchWishes();
    });
    return req;
  }

  deleteWish(id: Number) {
    this.http.delete(`${API_DB_URL}delete-wish/${id}`).subscribe(() => {
      this.fetchWishes();
    });
  }

  convertImage(image: Uint8Array): Observable<Uint8Array> {
    const image_base64 = uint8ArrayToBase64(image);
    return this.http
      .post(`${API_DB_URL}convert-image`, JSON.stringify({ image_base64 }), {
        headers: this.headers,
      })
      .pipe(map((res) => z.string().parse(res)))
      .pipe(map((res) => base64ToUint8Array(res)));
  }
}
