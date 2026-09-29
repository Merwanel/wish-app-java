import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { WishesComponent } from './wishes.component';
import { WishService } from '../wish.service';
import { TagsService } from '../tags.service';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { WishWRate } from '../../schemas/wish.schema';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('WishesComponent', () => {
  let component: WishesComponent;
  let fixture: ComponentFixture<WishesComponent>;
  let wishService: jasmine.SpyObj<WishService>;
  let tagsService: jasmine.SpyObj<TagsService>;
  let router: jasmine.SpyObj<Router>;
  let queryParams: BehaviorSubject<any>;

  let mutableMockWishes: WishWRate[];

  const initialMockWishes: WishWRate[] = [
    {
      id: 1,
      name: { val: 'Wish 1', letters: [] },
      comment: { val: 'Comment 1', letters: [] },
      tags: [{ id: '1', val: 'tag1', letters: [] }],
      createdAt: new Date().toISOString(),
      picture: new Uint8Array(),
    },
    {
      id: 2,
      name: { val: 'Wish 2', letters: [] },
      comment: { val: 'Comment 2', letters: [] },
      tags: [{ id: '2', val: 'tag2', letters: [] }],
      createdAt: new Date().toISOString(),
      picture: new Uint8Array(),
    },
  ];

  beforeEach(async () => {
    mutableMockWishes = JSON.parse(JSON.stringify(initialMockWishes));

    wishService = jasmine.createSpyObj(
      'WishService',
      ['fetchWishes', 'searchWishes', 'forceDetectChange', 'setWish'],
      {
        getWishes: mutableMockWishes,
        getSearchWords: [],
        getTotal: mutableMockWishes.length,
        getTagFacets: [],
      }
    );
    wishService.fetchWishes.and.returnValue(Promise.resolve());
    wishService.searchWishes.and.callFake(async () => {
      /* state already on spy getters */
    });

    Object.defineProperty(wishService, 'getWishes', {
      get: jasmine.createSpy('getWishes').and.callFake(() => mutableMockWishes),
    });
    Object.defineProperty(wishService, 'getTotal', {
      get: () => mutableMockWishes.length,
    });
    Object.defineProperty(wishService, 'getTagFacets', {
      get: () => [],
    });

    tagsService = jasmine.createSpyObj('TagsService', ['buildTags', 'setTagsFromFacets']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    queryParams = new BehaviorSubject({});

    await TestBed.configureTestingModule({
      providers: [
        { provide: WishService, useValue: wishService },
        { provide: TagsService, useValue: tagsService },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: queryParams.asObservable(),
          },
        },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WishesComponent);
    component = fixture.componentInstance;
    component.ngOnInit();
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should search wishes on init via /wishes/search path', () => {
    expect(wishService.searchWishes).toHaveBeenCalled();
  });

  it('should handle query params changes', fakeAsync(() => {
    queryParams.next({ page: '2', size: '5', search: 'test+query' });
    tick();

    expect(component.currentPage).toBe(2);
    expect(component.itemsPerPage).toBe(5);
    expect(component.search_words).toEqual(['test', 'query']);
  }));

  it('should navigate to next page', fakeAsync(async () => {
    component.currentPage = 1;
    component.totalPages = 2;

    await component.nextPage();

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({
        queryParams: jasmine.objectContaining({
          page: 2,
        }),
      })
    );
  }));

  it('should navigate to previous page', fakeAsync(async () => {
    component.currentPage = 2;
    component.totalPages = 2;

    await component.prevPage();

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({
        queryParams: jasmine.objectContaining({
          page: 1,
        }),
      })
    );
  }));

  it('should wrap around to last page when going previous from first page', fakeAsync(async () => {
    component.currentPage = 1;
    component.totalPages = 3;

    await component.prevPage();

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({
        queryParams: jasmine.objectContaining({
          page: 3,
        }),
      })
    );
  }));

  it('should wrap around to first page when going next from last page', fakeAsync(async () => {
    component.currentPage = 3;
    component.totalPages = 3;

    await component.nextPage();

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({
        queryParams: jasmine.objectContaining({
          page: 1,
        }),
      })
    );
  }));

  it('should toggle add mode', () => {
    expect(component.adding).toBeFalse();
    component.onToogleAdd();
    expect(component.adding).toBeTrue();
    component.onToogleAdd();
    expect(component.adding).toBeFalse();
  });

  it('should change display mode', () => {
    component.onClickDoChangeDisplayMode('display-list');
    expect(component.display_mode).toBe('display-list');

    component.onClickDoChangeDisplayMode('display-big-images');
    expect(component.display_mode).toBe('display-big-images');
  });

  it('should handle wish updates by reloading from API', async () => {
    const updatedWish = { ...mutableMockWishes[0], name: { val: 'Updated Wish', letters: [] } };
    await component.onWishUpdatedDoUpdateWish({ wish: updatedWish, idx: 0 });

    expect(wishService.setWish).toHaveBeenCalledWith(updatedWish, 0);
    expect(wishService.forceDetectChange).toHaveBeenCalled();
    expect(wishService.searchWishes).toHaveBeenCalled();
  });

  it('should handle wish deletion by reloading from API', async () => {
    await component.onWishDeletedDoUpdateDisplay({ idx: 0 });
    expect(wishService.searchWishes).toHaveBeenCalled();
  });
});
