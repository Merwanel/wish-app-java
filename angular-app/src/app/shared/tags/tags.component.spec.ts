import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TagsComponent } from './tags.component';
import { TagComponent } from './tag/tag.component';
import { WishService } from '../../wish.service';
import { Wish } from '../../../schemas/wish.schema';
import { of } from 'rxjs';

describe('TagsComponent', () => {
  let component: TagsComponent;
  let fixture: ComponentFixture<TagsComponent>;
  let mockWishService: jasmine.SpyObj<WishService>;

  function clickAddTag () {
    const addButton = fixture.debugElement.nativeElement.querySelector('.add-tag-button');
    expect(addButton).toBeTruthy();
    addButton.click();
  }

  const createMockWish = (tags: Array<{id: string, val: string, letters: any[]}> = []): Wish => ({
    id: 1,
    createdAt: '2023-01-01T00:00:00.000Z',
    name: { val: 'Test Wish', letters: [] },
    comment: { val: 'Test Comment', letters: [] },
    tags: tags,
    picture: new Uint8Array([1, 2, 3])
  });

  const createMockTag = (id: string, val: string) => ({
    id,
    val,
    letters: val.split('').map(c => ({ letter: c, is_a_match: false }))
  });

  beforeEach(async () => {
    const wishServiceSpy = jasmine.createSpyObj('WishService', ['updateWish']);
    wishServiceSpy.updateWish.and.returnValue(of({}));

    await TestBed.configureTestingModule({
      imports: [TagsComponent, TagComponent],
      providers: [
        { provide: WishService, useValue: wishServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TagsComponent);
    component = fixture.componentInstance;
    mockWishService = TestBed.inject(WishService) as jasmine.SpyObj<WishService>;
    
    component.wish = createMockWish();
    component.doUpdateWish = true;
    
    fixture.detectChanges();
  });

  describe('Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should render tags collection when wish has tags', () => {
      const tags = [
        createMockTag('1', 'tag1'),
        createMockTag('2', 'tag2')
      ];
      component.wish = createMockWish(tags);
      fixture.detectChanges();

      const tagElements = fixture.debugElement.nativeElement.querySelectorAll('app-tag');
      expect(tagElements.length).toBe(2);
    });

    it('should render add button', () => {
      const addButton = fixture.debugElement.nativeElement.querySelector('.add-tag-button');
      expect(addButton).toBeTruthy();
      expect(addButton.textContent.trim()).toBe('+');
    });
  });

  describe('Tag Addition', () => {
    it('should add new tag when clicking add button and no empty tag exists', () => {
      const initialTagCount = component.wish.tags.length;
      
      clickAddTag();
      
      expect(component.wish.tags.length).toBe(initialTagCount + 1);
      expect(component.wish.tags[component.wish.tags.length - 1]).toEqual({
        id: 'adding',
        val: '',
        letters: []
      });
    });

    it('should not add new tag when last tag is empty', () => {
      component.wish.tags = [createMockTag('1', '')];
      const initialTagCount = component.wish.tags.length;
      
      clickAddTag();
      
      expect(component.wish.tags.length).toBe(initialTagCount);
    });

    it('should add tag when all existing tags have values', () => {
      component.wish.tags = [createMockTag('1', 'existing')];
      
      clickAddTag();
      
      expect(component.wish.tags.length).toBe(2);
      expect(component.wish.tags[1].id).toBe('adding');
      expect(component.wish.tags[1].val).toBe('');
    });
  });

  describe('Tag Management', () => {
    it('should remove tag when tag value becomes empty', () => {
      component.wish.tags = [
        createMockTag('1', 'tag1'),
        createMockTag('2', 'tag2')
      ];
      
      const payload = {
        index: 0,
        tag: createMockTag('1', ''),
        doUpdateWish: false
      };
      
      component.onChangeDoUpdateWishTags(payload);
      
      expect(component.wish.tags.length).toBe(1);
      expect(component.wish.tags[0].val).toBe('tag2');
    });

    it('should update tag when tag value changes', () => {
      component.wish.tags = [createMockTag('1', 'oldvalue')];
      
      const updatedTag = createMockTag('1', 'newvalue');
      const payload = {
        index: 0,
        tag: updatedTag,
        doUpdateWish: false
      };
      
      component.onChangeDoUpdateWishTags(payload);
      
      expect(component.wish.tags[0].val).toBe('newvalue');
    });

    it('should not process changes for tags still being added with empty value', () => {
      component.wish.tags = [createMockTag('adding', '')];
      const initialTags = [...component.wish.tags];
      
      const payload = {
        index: 0,
        tag: createMockTag('adding', ''),
        doUpdateWish: false
      };
      
      component.onChangeDoUpdateWishTags(payload);
      
      expect(component.wish.tags).toEqual(initialTags);
    });
  });

  describe('Tag Deduplication', () => {
    it('should not process duplicate tag addition', () => {
      component.wish.tags = [
        createMockTag('1', 'existing'),
        createMockTag('adding', 'existing')
      ];
      const initialTags = [...component.wish.tags];
      
      const payload = {
        index: 1,
        tag: createMockTag('adding', 'existing'),
        doUpdateWish: false
      };
      
      component.onChangeDoUpdateWishTags(payload);
      
      expect(component.wish.tags).toEqual(initialTags);
    });
  });

  describe('WishService Integration', () => {
    it('should call WishService.updateWish when doUpdateWish is true', () => {
      component.wish.tags = [createMockTag('1', 'tag1')];
      
      const payload = {
        index: 0,
        tag: createMockTag('1', 'updated'),
        doUpdateWish: true
      };
      
      component.onChangeDoUpdateWishTags(payload);
      
      expect(mockWishService.updateWish).toHaveBeenCalledWith(component.wish.id, {
        tags: ['updated'],
      });
    });

    it('should not call WishService.updateWish when doUpdateWish is false', () => {
      component.wish.tags = [createMockTag('1', 'tag1')];
      
      const payload = {
        index: 0,
        tag: createMockTag('1', 'updated'),
        doUpdateWish: false
      };
      
      component.onChangeDoUpdateWishTags(payload);
      
      expect(mockWishService.updateWish).not.toHaveBeenCalled();
    });
  });

  describe('Tag Cleanup', () => {
    it('should remove trailing empty tag after processing', () => {
      component.wish.tags = [
        createMockTag('1', 'tag1'),
        createMockTag('2', '')
      ];
      
      const payload = {
        index: 0,
        tag: createMockTag('1', 'updated'),
        doUpdateWish: false
      };
      
      component.onChangeDoUpdateWishTags(payload);
      
      expect(component.wish.tags.length).toBe(1);
      expect(component.wish.tags[0].val).toBe('updated');
    });
  });
});