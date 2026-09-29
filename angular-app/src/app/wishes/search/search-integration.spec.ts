import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { By } from '@angular/platform-browser';

import { WishesComponent } from '../wishes.component';
import { SearchComponent } from './search.component';
import { WishService } from '../../wish.service';
import { TagsService } from '../../tags.service';
import { WishWRate } from '../../../schemas/wish.schema';
import { initLetters } from '../../utils/letters';
import { provideHttpClient } from '@angular/common/http';

describe('SearchComponent Integration Tests', () => {
  let parentComponent: WishesComponent;
  let searchComponent: SearchComponent;
  let parentFixture: ComponentFixture<WishesComponent>;
  let wishService: WishService;
  let tagsService: TagsService;
  let router: Router;
  let activatedRoute: ActivatedRoute;

  const mockWishes: WishWRate[] = [
    {
      id: 1,
      name: { val: 'Test Wish 1', letters: initLetters('Test Wish 1') },
      comment: { val: 'First test wish', letters: initLetters('First test wish') },
      tags: [{ id: 'tag1', val: 'test', letters: initLetters('test') }],
      createdAt: '2023-01-01T00:00:00.000Z',
      picture: new Uint8Array(),
      matchRate: 1
    },
    {
      id: 2,
      name: { val: 'Another Wish', letters: initLetters('Another Wish') },
      comment: { val: 'Second test wish', letters: initLetters('Second test wish') },
      tags: [{ id: 'tag2', val: 'example', letters: initLetters('example') }],
      createdAt: '2023-01-02T00:00:00.000Z',
      picture: new Uint8Array(),
      matchRate: 1
    }
  ];

  const queryParamsSubject = new BehaviorSubject<{
    page: number;
    size: number;
    search: string | null;
  }>({
    page: 1,
    size: 12,
    search: null
  });

  beforeEach(async () => {
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    const activatedRouteSpy = {
      queryParams: queryParamsSubject.asObservable()
    };

    await TestBed.configureTestingModule({
      imports: [
        WishesComponent,
      ],
      providers: [
        WishService,
        TagsService,
        { provide: Router, useValue: routerSpy },
        { provide: ActivatedRoute, useValue: activatedRouteSpy },
        provideHttpClient()
      ]
    }).compileComponents();

    parentFixture = TestBed.createComponent(WishesComponent);
    parentComponent = parentFixture.componentInstance;
    wishService = TestBed.inject(WishService);
    tagsService = TestBed.inject(TagsService);
    router = TestBed.inject(Router);
    activatedRoute = TestBed.inject(ActivatedRoute);

    spyOn(wishService, 'searchWishes').and.returnValue(Promise.resolve());
    spyOn(wishService, 'fetchWishes').and.returnValue(Promise.resolve());
    spyOnProperty(wishService, 'getWishes', 'get').and.returnValue(mockWishes);
    spyOnProperty(wishService, 'getTotal', 'get').and.returnValue(mockWishes.length);
    spyOnProperty(wishService, 'getTagFacets', 'get').and.returnValue([]);


    wishService.setWishes = mockWishes;

    parentFixture.detectChanges();

    searchComponent = parentFixture.debugElement.query(By.directive(SearchComponent))?.componentInstance;
  });

  describe('Test Environment Setup', () => {
    it('should create parent WishesComponent with SearchComponent child', () => {
      expect(parentComponent).toBeTruthy();
      expect(searchComponent).toBeTruthy();
    });

    it('should have real dependencies injected', () => {
      expect(wishService).toBeTruthy();
      expect(tagsService).toBeTruthy();
      expect(router).toBeTruthy();
      expect(activatedRoute).toBeTruthy();
    });

    it('should configure full component tree', () => {
      const searchElement = parentFixture.debugElement.query(By.directive(SearchComponent));
      expect(searchElement).toBeTruthy();
      expect(searchElement.componentInstance).toBe(searchComponent);
    });
  });

  describe('Parent-Child Workflows', () => {
    beforeEach(() => {
      if (searchComponent) {
        searchComponent.val = '';
        searchComponent.ngOnInit();
      }
    });

    it('should pass search value from parent to child component', () => {
      queryParamsSubject.next({
        page: 1,
        size: 12,
        search: 'test+search'
      });

      parentFixture.detectChanges();

      expect(parentComponent.search_words).toEqual(['test', 'search']);
    });

    it('should handle SearchComponent.searchChange event in parent', async () => {
      const testSearchValue = 'integration test';
      spyOn(parentComponent, 'onSearchChangeUpdateURL').and.callThrough();

      if (searchComponent) {
        searchComponent.searchChange.emit(testSearchValue);
      }

      parentFixture.detectChanges();

      expect(parentComponent.onSearchChangeUpdateURL).toHaveBeenCalledWith(testSearchValue);
    });

    it('should update router navigation when search changes', async () => {
      const testSearchValue = 'router test';

      await parentComponent.onSearchChangeUpdateURL(testSearchValue);

      expect(router.navigate).toHaveBeenCalledWith([], {
        relativeTo: activatedRoute,
        queryParams: {
          page: 1,
          size: parentComponent.itemsPerPage,
          search: 'router+test'
        },
        queryParamsHandling: 'merge'
      });
    });

    it('should synchronize search state between parent and child', () => {
      const searchValue = 'sync test';

      parentComponent.search_words = searchValue.split(' ');
      parentFixture.detectChanges()

      expect(searchComponent.search.val).toBe(searchValue);
    });
  });

  describe('Search Workflow Scenarios', () => {
    it('should handle empty search input correctly', async () => {
      await parentComponent.onSearchChangeUpdateURL('');

      expect(parentComponent.search_words).toEqual([]);
      expect(router.navigate).toHaveBeenCalledWith([], {
        relativeTo: activatedRoute,
        queryParams: {
          page: 1,
          size: parentComponent.itemsPerPage,
          search: null
        },
        queryParamsHandling: 'merge'
      });
    });

    it('should handle multiple search words', async () => {
      const multiWordSearch = 'multiple word search test';

      await parentComponent.onSearchChangeUpdateURL(multiWordSearch);

      expect(parentComponent.search_words).toEqual(['multiple', 'word', 'search', 'test']);
      expect(router.navigate).toHaveBeenCalledWith([], {
        relativeTo: activatedRoute,
        queryParams: {
          page: 1,
          size: parentComponent.itemsPerPage,
          search: 'multiple+word+search+test'
        },
        queryParamsHandling: 'merge'
      });
    });

    it('should filter out empty words from search', async () => {
      const searchWithSpaces = '  word1    word2  ';

      await parentComponent.onSearchChangeUpdateURL(searchWithSpaces);

      expect(parentComponent.search_words).toEqual(['word1', 'word2']);
    });

    it('should reset to page 1 when performing new search', async () => {
      parentComponent.currentPage = 5;

      await parentComponent.onSearchChangeUpdateURL('new search');

      expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({
        queryParams: jasmine.objectContaining({
          page: 1
        })
      }));
    });
  });

  describe('Component initialization', () => {
    it('should initialize search component with correct default values', () => {
      expect(searchComponent?.val).toBeDefined();
      expect(searchComponent?.search).toBeDefined();
      expect(searchComponent?.searchChange).toBeDefined();
    });

    it('wishesComponent should initialize with query parameters', () => {
      queryParamsSubject.next({
        page: 2,
        size: 10,
        search: 'initial+search'
      });

      expect(parentComponent.currentPage).toBe(2);
      expect(parentComponent.itemsPerPage).toBe(10);
      expect(parentComponent.search_words).toEqual(['initial', 'search']);
    });
  });

  describe('Real Dependency', () => {
    it('should integrate with WishService for search functionality', () => {
      const searchWords = ['test', 'wish'];
      parentComponent.search_words = searchWords;
      expect(wishService.getWishes).toBeDefined();
      expect(wishService.getSearchWords).toBeDefined();
    });

    it('should integrate with TagsService', () => {
      expect(tagsService).toBeTruthy();
      expect(tagsService.WishService).toBe(wishService);
    });

    it('should handle router navigation integration', async () => {
      const testSearch = 'navigation test';

      parentComponent.onSearchChangeUpdateURL(testSearch);
      // debounceTime(300)
      await new Promise((r) => setTimeout(r, 350));

      expect(router.navigate).toHaveBeenCalled();
      const navigationCall = (router.navigate as jasmine.Spy).calls.mostRecent();
      expect(navigationCall.args[0]).toEqual([]);
      expect(navigationCall.args[1]).toEqual(jasmine.objectContaining({
        relativeTo: activatedRoute,
        queryParamsHandling: 'merge'
      }));
    });
  });

  describe('Error Handling', () => {
    it('should handle search component event emission gracefully', () => {
      spyOn(console, 'error');

      if (searchComponent) {
        searchComponent.search.val = null as any;

        expect(() => {
          searchComponent.onInputDoEmit();
        }).not.toThrow();
      }
    });
  });
});