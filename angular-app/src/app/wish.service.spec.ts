import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WishService } from './wish.service';
import { API_DB_URL } from '../const';
import { WishOmitId, WishToSend } from '../schemas/wish.schema';
import { provideHttpClient } from '@angular/common/http';

describe('WishService', () => {
  let service: WishService;
  let httpMock: HttpTestingController;

  const searchResponse = (wishes: WishToSend[]) => ({
    total: wishes.length,
    wishes: wishes.map((w) => ({
      ...w,
      highlightedName: null,
      highlightedComment: null,
    })),
    aggregations: {
      tags: wishes.flatMap((w) => w.tags).map((key) => ({ key, count: 1 })),
    },
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [WishService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(WishService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('searchWishes / fetchWishes', () => {
    it('should fetch via /wishes/search and update service state', async () => {
      const mockWishes: WishToSend[] = [
        {
          id: 1,
          name: 'Test Wish',
          comment: 'Test Comment',
          tags: ['test'],
          createdAt: new Date().toISOString(),
          picture: '',
        },
      ];

      const fetchPromise = service.fetchWishes();

      const req = httpMock.expectOne((r) => r.url === `${API_DB_URL}wishes/search`);
      expect(req.request.method).toBe('GET');
      req.flush(searchResponse(mockWishes));

      await fetchPromise;

      expect(service.getWishes.length).toBe(1);
      expect(service.getWishes[0].name.val).toBe('Test Wish');
      expect(service.getTotal).toBe(1);
    });

    it('should search with q and populate highlights', async () => {
      const promise = service.searchWishes({ q: 'zelda' });
      const req = httpMock.expectOne(
        (r) => r.url === `${API_DB_URL}wishes/search` && r.params.get('q') === 'zelda'
      );
      req.flush({
        total: 1,
        wishes: [
          {
            id: 1,
            name: 'Zelda Breath of the Wild',
            comment: 'Best game',
            tags: ['gaming'],
            createdAt: new Date().toISOString(),
            picture: '',
            highlightedName: '<mark>Zelda</mark> Breath of the Wild',
            highlightedComment: null,
          },
        ],
        aggregations: { tags: [{ key: 'gaming', count: 1 }] },
      });
      await promise;
      expect(service.getWishes[0].highlightedName).toContain('<mark>Zelda</mark>');
      expect(service.getTagFacets).toEqual([{ key: 'gaming', count: 1 }]);
    });
  });

  describe('addWish', () => {
    it('should send POST request and refresh wishes with updated data', () => {
      const mockWish: WishOmitId = {
        name: 'New Wish',
        comment: 'New Comment',
        tags: ['new'],
        createdAt: new Date().toISOString(),
        picture: new Uint8Array(),
      };

      const mockResponse: WishToSend[] = [
        {
          id: 1,
          name: 'New Wish',
          comment: 'New Comment',
          tags: ['new'],
          createdAt: mockWish.createdAt,
          picture: '',
        },
      ];

      service.addWish(mockWish).subscribe({
        complete: () => {
          expect(service.getWishes.length).toBe(1);
          expect(service.getWishes[0].name.val).toBe('New Wish');
          expect(service.getWishes[0].comment.val).toBe('New Comment');
          expect(service.getWishes[0].tags[0].val).toBe('new');
        },
      });

      const addReq = httpMock.expectOne(`${API_DB_URL}new-wish`);
      expect(addReq.request.method).toBe('POST');
      addReq.flush({ success: true });

      const fetchReq = httpMock.expectOne((r) => r.url === `${API_DB_URL}wishes/search`);
      expect(fetchReq.request.method).toBe('GET');
      fetchReq.flush(searchResponse(mockResponse));
    });
  });
});
