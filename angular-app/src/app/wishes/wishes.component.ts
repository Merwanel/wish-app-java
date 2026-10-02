import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { WishService } from '../wish.service';
import { WishComponent } from './wish/wish.component';
import { TagsService } from '../tags.service';
import { AddWishComponent } from './add-wish/add-wish.component';
import { SearchComponent } from './search/search.component';
import { BetterSelectComponent, option } from '../../ui/better-select/better-select.component';
import { PaginationComponent } from '../../ui/pagination/pagination.component';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { WishWRate } from '../../schemas/wish.schema';

export type DisplayMode = 'display-big-images' | 'display-list';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-wishes',
  imports: [WishComponent, AddWishComponent, SearchComponent, BetterSelectComponent, PaginationComponent],
  templateUrl: './wishes.component.html',
  styleUrl: './wishes.component.css',
})
export class WishesComponent implements OnInit, OnDestroy {
  display_wishes: { ori_idx: number; wish: WishWRate }[] = [];
  wishes_matching: WishWRate[] = [];

  adding = false;
  options: option[] = [
    { val: '☐ big images', selected_val: '☐', val_to_emit: 'display-big-images' },
    { val: '☰ list', selected_val: '☰', val_to_emit: 'display-list' },
  ];
  display_mode: DisplayMode = this.options[0].val_to_emit;

  currentPage = 1;
  itemsPerPage = 10;
  totalPages = 0;
  search_words: string[] = [];
  selectedTag: string | null = null;

  private routeSubscription!: Subscription;
  private searchInput$ = new Subject<string>();
  private searchSubscription!: Subscription;
  private reloadGeneration = 0;

  constructor(
    public WishService: WishService,
    public TagsService: TagsService,
    private activatedRoute: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit() {
    this.searchSubscription = this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe((searchValue) => {
        void this.navigateSearch(searchValue);
      });

    this.routeSubscription = this.activatedRoute.queryParams.subscribe((params: Params) => {
      this.currentPage = Number(params['page']) || 1;
      if (Number(params['size']) > 0) this.itemsPerPage = Number(params['size']);
      this.search_words = params['search'] ? String(params['search']).split('+').filter(Boolean) : [];
      this.selectedTag = params['tag'] ? String(params['tag']) : null;
      void this.reloadFromApi();
    });
  }

  ngOnDestroy() {
    this.routeSubscription.unsubscribe();
    this.searchSubscription.unsubscribe();
  }

  private async reloadFromApi() {
    const generation = ++this.reloadGeneration;
    const q = this.search_words.join(' ');
    const offset = (this.currentPage - 1) * this.itemsPerPage;
    await this.WishService.searchWishes({
      q,
      tag: this.selectedTag,
      limit: this.itemsPerPage,
      offset,
      tagsService: this.TagsService,
    });
    if (generation !== this.reloadGeneration) {
      return;
    }
    this.wishes_matching = this.WishService.getWishes;
    this.totalPages = Math.max(1, Math.ceil(this.WishService.getTotal / this.itemsPerPage));
    this.display_wishes = this.wishes_matching.map((wish, idx) => ({
      ori_idx: idx,
      wish,
    }));
  }

  async nextPage() {
    await this.goToPage(this.currentPage + 1);
  }
  async prevPage() {
    await this.goToPage(this.currentPage - 1);
  }

  async goToPage(page: number) {
    if (page < 1) {
      page = this.totalPages;
    }
    if (page > this.totalPages) {
      page = 1;
    }
    await this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: {
        page,
        size: this.itemsPerPage,
        search: this.search_words.length > 0 ? this.search_words.join('+') : null,
        tag: this.selectedTag,
      },
      queryParamsHandling: 'merge',
    });
  }

  onToogleAdd() {
    this.adding = !this.adding;
  }
  onClickDoChangeDisplayMode(new_display_mode: DisplayMode) {
    this.display_mode = new_display_mode;
  }
  onWishUpdatedDoUpdateWish(event: { wish: WishWRate; idx: number }) {
    this.WishService.setWish(event.wish, event.idx);
    this.WishService.forceDetectChange();
    void this.reloadFromApi();
  }
  onWishDeletedDoUpdateDisplay(_event: { idx: number }) {
    void this.reloadFromApi();
  }
  onWishAddedDoUpdateDisplay() {
    void this.reloadFromApi();
  }

  onSearchChangeUpdateURL(searchValue: string) {
    this.searchInput$.next(searchValue || '');
  }

  private async navigateSearch(searchValue: string) {
    const searchWords = searchValue.trim()
      ? searchValue.split(' ').filter((word) => word.length > 0)
      : [];
    this.search_words = searchWords;
    await this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: {
        page: 1,
        size: this.itemsPerPage,
        search: searchWords.length > 0 ? searchWords.join('+') : null,
        tag: this.selectedTag,
      },
      queryParamsHandling: 'merge',
    });
  }

  async onFacetClick(tagKey: string) {
    const next = this.selectedTag === tagKey ? null : tagKey;
    this.selectedTag = next;
    await this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: {
        page: 1,
        size: this.itemsPerPage,
        search: this.search_words.length > 0 ? this.search_words.join('+') : null,
        tag: next,
      },
      queryParamsHandling: 'merge',
    });
  }

  async onChangeDoUpdatePerPage(event: Event) {
    this.itemsPerPage = Number((event.target as HTMLSelectElement).value);
    await this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: {
        page: 1,
        size: this.itemsPerPage,
        search: this.search_words.length > 0 ? this.search_words.join('+') : null,
        tag: this.selectedTag,
      },
      queryParamsHandling: 'merge',
    });
  }
}
