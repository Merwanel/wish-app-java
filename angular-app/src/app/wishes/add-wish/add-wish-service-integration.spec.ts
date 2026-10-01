import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AddWishComponent } from './add-wish.component';
import { WishService } from '../../wish.service';
import { TagsService } from '../../tags.service';
import { DynamicInputComponent } from '../../../ui/dynamic-input/dynamic-input.component';
import { TagsComponent } from '../../shared/tags/tags.component';
import { TagComponent } from '../../shared/tags/tag/tag.component';
import { Wish, WishWRate } from '../../../schemas/wish.schema';
import { initLetters } from '../../utils/letters';
import { API_DB_URL } from '../../../const';
import { uint8ArrayToBase64 } from 'uint8array-extras';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';

describe('AddWishComponent Integration Tests', () => {
  let component: AddWishComponent;
  let fixture: ComponentFixture<AddWishComponent>;
  let wishService: WishService;
  let tagsService: TagsService;
  let httpMock: HttpTestingController;

  function submitNewWish() {
    const formDebugElement = fixture.debugElement.query(By.css('form'));
    expect(formDebugElement).toBeTruthy();

    formDebugElement.triggerEventHandler('ngSubmit');
    fixture.detectChanges();
  }


  const PNG_header = [137, 80, 78, 71, 13, 10, 26, 10];

  const createMockWish = (overrides: Partial<Wish> = {}): Wish => ({
    id: 1,
    name: { val: 'Test Wish', letters: initLetters('Test Wish') },
    comment: { val: 'Test Comment', letters: initLetters('Test Comment') },
    tags: [
      { id: 'tag1', val: 'gaming', letters: initLetters('gaming') },
      { id: 'tag2', val: 'adventure', letters: initLetters('adventure') }
    ],
    picture: new Uint8Array([1, 2, 3, 4]),
    createdAt: '2023-01-01T00:00:00.000Z',
    ...overrides
  });

  const createMockWishWRate = (overrides: Partial<WishWRate> = {}): WishWRate => ({
    ...createMockWish(),
    matchRate: 1,
    track_id: '2023-01-01T00:00:00.000Z1',
    ...overrides
  });

  const createMockApiWish = (overrides: any = {}) => ({
    id: 1,
    name: 'Test Wish',
    comment: 'Test Comment',
    tags: ['gaming', 'adventure'],
    picture: uint8ArrayToBase64(new Uint8Array([1, 2, 3, 4])),
    createdAt: '2023-01-01T00:00:00.000Z',
    ...overrides
  });

  const toSearchResponse = (wishes: ReturnType<typeof createMockApiWish>[] = []) => {
    const tagCounts = new Map<string, number>();
    wishes.forEach((w) => w.tags.forEach((t: string) => tagCounts.set(t, (tagCounts.get(t) || 0) + 1)));
    return {
      total: wishes.length,
      wishes: wishes.map((w) => ({ ...w, highlightedName: null, highlightedComment: null })),
      aggregations: {
        tags: Array.from(tagCounts.entries()).map(([key, count]) => ({ key, count })),
      },
    };
  };

  const expectSearchRequest = () => {
    const req = httpMock.expectOne((r) => r.url === `${API_DB_URL}wishes/search`);
    expect(req.request.method).toBe('GET');
    return req;
  };

  const createMockFile = (name: string = 'test.jpg', type: string = 'image/jpeg'): File => {
    const buffer = new ArrayBuffer(8);
    const view = new Uint8Array(buffer);
    view.set(PNG_header);
    return new File([buffer], name, { type });
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AddWishComponent,
        FormsModule,
        DynamicInputComponent,
        TagsComponent,
        TagComponent
      ],
      providers: [
        WishService,
        TagsService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AddWishComponent);
    component = fixture.componentInstance;
    wishService = TestBed.inject(WishService);
    tagsService = TestBed.inject(TagsService);
    httpMock = TestBed.inject(HttpTestingController);


  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('Complete Wish Creation Workflow', () => {
    it('should complete full wish creation workflow from form to service', () => {
      const existingWishes = [createMockWishWRate({ id: 1 })];
      wishService.setWishes = existingWishes;
      tagsService.buildTags();

      component.wish.name.val = 'New Game Wish';
      component.wish.comment.val = 'Looking forward to this game';
      component.wish.tags = [
        { id: 'tag1', val: 'gaming', letters: initLetters('gaming') },
        { id: 'tag2', val: 'rpg', letters: initLetters('rpg') }
      ];
      component.imageBuffer = new Uint8Array([5, 6, 7, 8]);

      fixture.detectChanges();

      submitNewWish();

      const req = httpMock.expectOne(`${API_DB_URL}new-wish`);
      expect(req.request.method).toBe('POST');
      expect(req.request.headers.get('Content-Type')).toBe('application/json');

      const requestBody = JSON.parse(req.request.body);
      expect(requestBody.name).toBe('New Game Wish');
      expect(requestBody.comment).toBe('Looking forward to this game');
      expect(requestBody.tags).toEqual(['gaming', 'rpg']);
      expect(requestBody.picture).toBe(uint8ArrayToBase64(component.imageBuffer));

      req.flush({ success: true });

      const fetchReq = expectSearchRequest();
      fetchReq.flush(toSearchResponse([]));
    });
  });

  describe('Tag Creation and Association During Wish Creation', () => {
    it('should have TagsComponnent displayed', () => {
      const tagsComponent = fixture.debugElement.nativeElement.querySelector('app-tags');
      expect(tagsComponent).toBeTruthy();
    })
    it('should create and associate new tags during wish creation', () => {
      const existingWishes = [
        createMockWishWRate({
          id: 1,
          tags: [{ id: 'tag1', val: 'existing', letters: initLetters('existing') }]
        })
      ];
      wishService.setWishes = existingWishes;
      tagsService.buildTags();

      expect(tagsService.getTags.has('existing')).toBe(true);
      expect(tagsService.getTags.has('newtag')).toBe(false);

      component.wish.name.val = 'Test Wish';
      component.wish.tags = [
        { id: 'tag1', val: 'existing', letters: initLetters('existing') },
        { id: 'tag2', val: 'newtag', letters: initLetters('newtag') }
      ];

      fixture.detectChanges();
      submitNewWish()

      const req = httpMock.expectOne(`${API_DB_URL}new-wish`);
      const requestBody = JSON.parse(req.request.body);
      expect(requestBody.tags).toEqual(['existing', 'newtag']);

      req.flush({ success: true });

      const fetchReq = expectSearchRequest();
      const updatedWishes = [
        createMockApiWish({ id: 1, tags: ['existing'] }),
        createMockApiWish({
          id: 2,
          name: 'Test Wish',
          tags: ['existing', 'newtag']
        })
      ];
      fetchReq.flush(toSearchResponse(updatedWishes));
    });

    it('should handle tag association with TagsComponent integration', () => {
      component.wish.name.val = 'Test Wish';
      fixture.detectChanges();

      component.wish.tags = [
        { id: 'tag1', val: 'action', letters: initLetters('action') },
        { id: 'tag2', val: 'multiplayer', letters: initLetters('multiplayer') }
      ];

      fixture.detectChanges();
      submitNewWish();

      const req = httpMock.expectOne(`${API_DB_URL}new-wish`);
      const requestBody = JSON.parse(req.request.body);
      expect(requestBody.tags).toEqual(['action', 'multiplayer']);

      req.flush({ success: true });

      expectSearchRequest().flush(toSearchResponse([]));
    });
  });

  describe('Image Upload and Processing Integration', () => {
    it('should integrate image upload with complete wish creation workflow', () => {
      const mockFile = createMockFile('test-image.png', 'image/png');
      const mockEvent = {
        target: { files: [mockFile] }
      } as any;

      const mockFileReader = {
        onload: null as any,
        result: null as any,
        readAsArrayBuffer: jasmine.createSpy('readAsArrayBuffer').and.callFake(function (this: any) {
          this.result = new ArrayBuffer(8);
          const view = new Uint8Array(this.result);
          view.set(PNG_header);
          if (this.onload) {
            this.onload();
          }
        })
      };


      const outputImage = new Uint8Array([4, 5, 6, 7]);

      spyOn(window, 'FileReader').and.returnValue(mockFileReader as any);
      spyOn(wishService, "convertImage").and.returnValue(of(outputImage));

      component.onFileSelectedDoDisplay(mockEvent);

      expect(wishService.convertImage).toHaveBeenCalledWith(new Uint8Array(PNG_header));
      expect(component.imageBuffer).toEqual(outputImage);


      component.wish.name.val = 'Wish with Image';
      fixture.detectChanges();
      submitNewWish();

      const req = httpMock.expectOne(`${API_DB_URL}new-wish`);
      const requestBody = JSON.parse(req.request.body);
      expect(requestBody.picture).toBe(uint8ArrayToBase64(component.imageBuffer));
      expect(requestBody.picture).not.toBe('');


      req.flush({ success: true });

      expectSearchRequest().flush(toSearchResponse([]));
    });

    it('should handle wish creation without image upload', () => {
      component.wish.name.val = 'Wish without Image';
      expect(component.imageBuffer).toEqual(new Uint8Array());

      fixture.detectChanges();
      submitNewWish();

      const req = httpMock.expectOne(`${API_DB_URL}new-wish`);
      const requestBody = JSON.parse(req.request.body);
      expect(requestBody.picture).toBe('');

      req.flush({ success: true });

      expectSearchRequest().flush(toSearchResponse([]));
    });
  });

  describe('Form Validation and Error Handling Integration', () => {
    it('should prevent submission with invalid form data and handle validation errors', () => {
      component.wish.name.val = 'ab';
      fixture.detectChanges();

      const createButton = fixture.debugElement.nativeElement.querySelector('.create-button');
      expect(createButton.disabled).toBe(true);

      const alertDiv = fixture.debugElement.nativeElement.querySelector('.alert');
      expect(alertDiv.textContent.trim()).toContain('Name must be at least 3 characters long.');

      httpMock.expectNone(`${API_DB_URL}new-wish`);
    });

    it('should handle HTTP error responses and verify error propagation', fakeAsync(() => {
      component.wish.name.val = 'Valid Wish Name';
      fixture.detectChanges();

      submitNewWish();

      const req = httpMock.expectOne(`${API_DB_URL}new-wish`);

      expect(() => {
        req.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
        tick();
      }).toThrow();

      expect(component).toBeTruthy();
    }));
  });

  describe('Wish List Updates After Successful Creation', () => {
    it('should update wish list and tags after successful wish creation', fakeAsync(() => {
      const initialWishes = [createMockWishWRate({ id: 1 })];
      wishService.setWishes = initialWishes;
      tagsService.buildTags();

      component.wish.name.val = 'New Wish';
      component.wish.tags = [
        { id: 'tag1', val: 'newtag', letters: initLetters('newtag') }
      ];

      fixture.detectChanges();
      submitNewWish();

      const createReq = httpMock.expectOne(`${API_DB_URL}new-wish`);
      createReq.flush({ success: true });

      const fetchReq = expectSearchRequest();
      const updatedWishes = [
        createMockApiWish({ id: 1 }),
        createMockApiWish({
          id: 2,
          name: 'New Wish',
          tags: ['newtag']
        })
      ];
      fetchReq.flush(toSearchResponse(updatedWishes));

      tick();

      expect(wishService.getWishes.length).toBe(2);
      expect(wishService.getWishes[1].name.val).toBe('New Wish');
    }));

    it('should maintain form state during creation process', fakeAsync(() => {
      const originalWishData = {
        name: 'Test Wish',
        comment: 'Test Comment',
        tags: [{ id: 'tag1', val: 'gaming', letters: initLetters('gaming') }]
      };

      component.wish.name.val = originalWishData.name;
      component.wish.comment.val = originalWishData.comment;
      component.wish.tags = originalWishData.tags;

      fixture.detectChanges();

      submitNewWish();

      expect(component.wish.name.val).toBe(originalWishData.name);
      expect(component.wish.comment.val).toBe(originalWishData.comment);
      expect(component.wish.tags).toEqual(originalWishData.tags);

      const req = httpMock.expectOne(`${API_DB_URL}new-wish`);
      req.flush({ success: true });

      expectSearchRequest().flush(toSearchResponse([]));

      tick();

      expect(component.wish.name.val).toBe(originalWishData.name);
      expect(component.wish.comment.val).toBe(originalWishData.comment);
      expect(component.wish.tags).toEqual(originalWishData.tags);
    }));
  });
});