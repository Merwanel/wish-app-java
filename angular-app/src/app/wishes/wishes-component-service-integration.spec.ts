import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router, ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { WishesComponent } from './wishes.component';
import { WishService } from '../wish.service';
import { TagsService } from '../tags.service';
import { WishOmitId, WishPartial } from '../../schemas/wish.schema';
import { uint8ArrayToBase64 } from 'uint8array-extras';
import { API_DB_URL } from '../../const';

interface MockApiWish {
  id: number;
  name: string;
  comment: string;
  tags: string[];
  picture: string;
  createdAt: string;
}

describe('WishesComponent - Service Integration', () => {
  let component: WishesComponent;
  let fixture: ComponentFixture<WishesComponent>;
  let wishService: WishService;
  let tagsService: TagsService;
  let httpMock: HttpTestingController;
  let mockRouter: jasmine.SpyObj<Router>;
  let queryParams: BehaviorSubject<any>;

  const createMockApiWish = (overrides: Partial<MockApiWish> = {}): MockApiWish => ({
    id: 1,
    name: 'Test Wish',
    comment: 'Test Comment',
    tags: ['gaming', 'adventure'],
    picture: uint8ArrayToBase64(new Uint8Array([1, 2, 3, 4])),
    createdAt: '2023-01-01T00:00:00.000Z',
    ...overrides
  });

  const createMockApiWishes = (count: number): MockApiWish[] => {
    return Array.from({ length: count }, (_, index) => 
      createMockApiWish({
        id: index + 1,
        name: `Test Wish ${index + 1}`,
        comment: `Test Comment ${index + 1}`,
        tags: [`tag${index + 1}`, 'common'],
      })
    );
  };

  const expectHttpRequest = (url: string, method: string): TestRequest => {
    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe(method);
    return req;
  };

  const flushHttpResponse = (req: TestRequest, response: any): void => {
    req.flush(response);
  };

  const toSearchResponse = (mockWishes: MockApiWish[] = []) => ({
    total: mockWishes.length,
    wishes: mockWishes.map(w => ({ ...w, highlightedName: null, highlightedComment: null })),
    aggregations: { tags: [] },
  });

  const expectAndFlushFetchWishes = (mockWishes: MockApiWish[] = []): void => {
    const req = httpMock.expectOne(r => r.url === `${API_DB_URL}wishes/search`);
    expect(req.request.method).toBe('GET');
    flushHttpResponse(req, toSearchResponse(mockWishes));
  };

  const waitForAsyncOperations = async (): Promise<void> => {
    await fixture.whenStable();
    fixture.detectChanges();
  };

  const createMockWishOmitId = (overrides: Partial<WishOmitId> = {}): WishOmitId => ({
    name: 'New Test Wish',
    comment: 'New Test Comment',
    tags: ['new', 'test'],
    picture: new Uint8Array([5, 6, 7, 8]),
    createdAt: '2023-01-02T00:00:00.000Z',
    ...overrides
  });

  const createMockWishPartial = (id: number, overrides: Partial<WishPartial> = {}): WishPartial => ({
    id,
    name: 'Updated Test Wish',
    comment: 'Updated Test Comment',
    tags: ['updated', 'test'],
    picture: new Uint8Array([9, 10, 11, 12]),
    ...overrides
  });

  const verifyRequestBody = (req: TestRequest, expectedBody: any): void => {
    const actualBody = typeof req.request.body === 'string' ? JSON.parse(req.request.body) : req.request.body;
    const expectedBodyParsed = typeof expectedBody === 'string' ? JSON.parse(expectedBody) : expectedBody;
    expect(actualBody).toEqual(expectedBodyParsed);
  };

  const verifyDisplayWishesCount = (expectedCount: number): void => {
    expect(component.display_wishes.length).toBe(expectedCount);
  };

  const verifyWishInDisplay = (wishId: number): void => {
    const found = component.display_wishes.some(dw => dw.wish.id === wishId);
    expect(found).toBe(true);
  };

  const verifyWishNotInDisplay = (wishId: number): void => {
    const found = component.display_wishes.some(dw => dw.wish.id === wishId);
    expect(found).toBe(false);
  };

  beforeEach(async () => {
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);
    queryParams = new BehaviorSubject({});

    await TestBed.configureTestingModule({
      providers: [
        WishService,
        TagsService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: queryParams.asObservable(),
            snapshot: { queryParams: {} }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(WishesComponent);
    component = fixture.componentInstance;
    wishService = TestBed.inject(WishService);
    tagsService = TestBed.inject(TagsService);
    httpMock = TestBed.inject(HttpTestingController);

    
    component.itemsPerPage = 10;
    component.currentPage = 1;
    
    wishService.setWishes = [];
    
    
    component.ngOnInit();
    fixture.detectChanges();
    
    const initialRequests = httpMock.match(r => r.url === `${API_DB_URL}wishes/search`);
    initialRequests.forEach(req => {
      expect(req.request.method).toBe('GET');
      req.flush(toSearchResponse([]));
    });
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create component and inject services correctly', () => {
    expect(component).toBeTruthy();
    expect(wishService).toBeTruthy();
    expect(tagsService).toBeTruthy();
    expect(httpMock).toBeTruthy();
  });

  it('should have all helper functions working correctly', () => {
    const mockApiWish = createMockApiWish({ name: 'Custom Wish' });
    expect(mockApiWish.name).toBe('Custom Wish');
    expect(mockApiWish.id).toBe(1);
    expect(mockApiWish.tags).toEqual(['gaming', 'adventure']);

    const mockWishes = createMockApiWishes(3);
    expect(mockWishes.length).toBe(3);
    expect(mockWishes[0].name).toBe('Test Wish 1');
    expect(mockWishes[2].name).toBe('Test Wish 3');

    const mockWishOmitId = createMockWishOmitId({ name: 'New Custom Wish' });
    expect(mockWishOmitId.name).toBe('New Custom Wish');
    expect(mockWishOmitId.createdAt).toBeDefined();

    const mockWishPartial = createMockWishPartial(5, { name: 'Updated Custom Wish' });
    expect(mockWishPartial.id).toBe(5);
    expect(mockWishPartial.name).toBe('Updated Custom Wish');
  });

  it('should create a new wish', async () => {
    const initialWishes = createMockApiWishes(2);
    
    const fetchPromise = wishService.fetchWishes(tagsService);
    expectAndFlushFetchWishes(initialWishes);
    await fetchPromise;
    
        fixture.detectChanges();
    
    verifyDisplayWishesCount(2);
    
    const newWishData = createMockWishOmitId({
      name: 'Integration Test Wish',
      comment: 'This wish was created through integration test',
      tags: ['integration', 'test', 'new'],
      picture: new Uint8Array([10, 20, 30, 40])
    });
        
    wishService.addWish(newWishData).subscribe({
      complete: () => {      
                fixture.detectChanges();
        
        verifyDisplayWishesCount(3);
        verifyWishInDisplay(3);
        
        const displayedNewWish = component.display_wishes.find(dw => dw.wish.id === 3);
        expect(displayedNewWish).toBeTruthy();
        expect(displayedNewWish!.wish.name.val).toBe('Integration Test Wish');
        expect(displayedNewWish!.wish.comment.val).toBe('This wish was created through integration test');
        expect(displayedNewWish!.wish.tags.map(t => t.val)).toEqual(['integration', 'test', 'new']);
        
        expect(component.display_wishes.length).toBe(3);
        expect(component.wishes_matching.length).toBe(3);
      },
    });
    
    const newWishReq = expectHttpRequest(`${API_DB_URL}new-wish`, 'POST');
    
    const expectedRequestBody = {
      name: newWishData.name,
      comment: newWishData.comment,
      tags: newWishData.tags,
      picture: uint8ArrayToBase64(newWishData.picture),
      createdAt: newWishData.createdAt
    };
    verifyRequestBody(newWishReq, JSON.stringify(expectedRequestBody));
    
    const postResponse = { success: true, id: 3 };
    flushHttpResponse(newWishReq, postResponse);
    
    const newWishInResponse = createMockApiWish({
      id: 3,
      name: newWishData.name,
      comment: newWishData.comment,
      tags: newWishData.tags,
      picture: uint8ArrayToBase64(newWishData.picture),
      createdAt: newWishData.createdAt
    });
    const updatedWishes = [...initialWishes, newWishInResponse];
    expectAndFlushFetchWishes(updatedWishes);
  });

  it('should delete an existing wish ', async () => {
    const initialWishes = createMockApiWishes(3);
    
    const fetchPromise = wishService.fetchWishes(tagsService);
    expectAndFlushFetchWishes(initialWishes);
    await fetchPromise;
    
        fixture.detectChanges();
    
    verifyDisplayWishesCount(3);

    const wishIdToDelete = 2;
    verifyWishInDisplay(wishIdToDelete);
    
    wishService.deleteWish(wishIdToDelete);
    
    const deleteReq = expectHttpRequest(`${API_DB_URL}delete-wish/${wishIdToDelete}`, 'DELETE');
    
    expect(deleteReq.request.url).toBe(`${API_DB_URL}delete-wish/${wishIdToDelete}`);
    
    const deleteResponse = { success: true, message: 'Wish deleted successfully' };
    flushHttpResponse(deleteReq, deleteResponse);
    
    const remainingWishes = initialWishes.filter(wish => wish.id !== wishIdToDelete);
    expectAndFlushFetchWishes(remainingWishes);
    
    await waitForAsyncOperations();
    
        fixture.detectChanges();
    
    verifyDisplayWishesCount(2);
    verifyWishNotInDisplay(wishIdToDelete);
  
    
    const displayedWishIds = component.display_wishes.map(dw => dw.wish.id);
    expect(displayedWishIds).not.toContain(wishIdToDelete);
    

    expect(component.display_wishes.length).toBe(2);
    expect(component.wishes_matching.length).toBe(2);
    

    const serviceWishes = wishService.getWishes;
    expect(serviceWishes.length).toBe(2);
    expect(serviceWishes.find(w => w.id === wishIdToDelete)).toBeUndefined();
  });
});