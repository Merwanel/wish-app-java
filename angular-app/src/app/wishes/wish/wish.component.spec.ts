import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { WishComponent } from './wish.component';
import { WishService } from '../../wish.service';
import { TagsComponent } from '../../shared/tags/tags.component';
import { DynamicTextAreaComponent } from '../../../ui/dynamic-text-area/dynamic-text-area.component';
import { Letter, WishWRate } from '../../../schemas/wish.schema';

describe('WishComponent', () => {
  let component: WishComponent;
  let fixture: ComponentFixture<WishComponent>;
  let wishService: jasmine.SpyObj<WishService>;

  const createMockLetter = (char: string): Letter => ({
    letter: char,
    is_a_match: false
  });

  const createMockWish = (
    id: number = 1,
    name: string = 'Test Wish',
    comment: string = 'Test comment',
    tags: string[] = ['tag1', 'tag2']
  ): WishWRate => ({
    id,
    name: {
      val: name,
      letters: name.split('').map(createMockLetter)
    },
    comment: {
      val: comment,
      letters: comment.split('').map(createMockLetter)
    },
    tags: tags.map((tag, index) => ({
      id: `tag-${id}-${index}`,
      val: tag,
      letters: tag.split('').map(createMockLetter)
    })),
    matchRate: 1,
    track_id: `2025-07-14T00:00:00.000Z${id}`,
    createdAt: '2025-07-14T00:00:00.000Z',
    picture: new Uint8Array([1, 2, 3, 4])
  });

  beforeEach(async () => {
    const wishServiceSpy = jasmine.createSpyObj('WishService', [
      'updateWish',
      'deleteWish'
    ]);

    await TestBed.configureTestingModule({
      imports: [
        WishComponent,
        FormsModule,
        TagsComponent,
        DynamicTextAreaComponent,
        DatePipe
      ],
      providers: [
        { provide: WishService, useValue: wishServiceSpy },
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(WishComponent);
    component = fixture.componentInstance;
    wishService = TestBed.inject(WishService) as jasmine.SpyObj<WishService>;

    component.display_mode = 'display-big-images';
    component.wish = createMockWish();
    component.idx = 0;
  });

  afterEach(() => {
    if (component.imagesrc && component.imagesrc.startsWith('blob:')) {
      URL.revokeObjectURL(component.imagesrc);
    }
    if ((component as any).oldImageSrc && (component as any).oldImageSrc.startsWith('blob:')) {
      URL.revokeObjectURL((component as any).oldImageSrc);
    }
  });

  describe('Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should initialize with required inputs', () => {
      expect(component.display_mode).toBe('display-big-images');
      expect(component.wish).toEqual(createMockWish());
      expect(component.idx).toBe(0);
    });

    it('should create image source on init when wish has picture', () => {
      spyOn(URL, 'createObjectURL').and.returnValue('blob:mock-url');
      
      component.ngOnInit();
      
      expect(URL.createObjectURL).toHaveBeenCalled();
      expect(component.imagesrc).toBe('blob:mock-url');
    });
  });

  describe('Wish Display and Rendering', () => {
    it('should render wish in display-list mode', () => {
      component.display_mode = 'display-list';
      fixture.detectChanges();

      const wishElement = fixture.debugElement.nativeElement.querySelector('.wish.display-list');
      expect(wishElement).toBeTruthy();
    });

    it('should display tags component', () => {
      fixture.detectChanges();

      const tagsComponent = fixture.debugElement.nativeElement.querySelector('app-tags');
      expect(tagsComponent).toBeTruthy();
    });
  });

  describe('Wish Interaction Events', () => {
    it('should emit wishDeleted event on delete button click', () => {
      spyOn(component.wishDeleted, 'emit');
      wishService.deleteWish.and.stub();

      component.onDeleteWish();

      expect(wishService.deleteWish).toHaveBeenCalledWith(component.wish.id);
      expect(component.wishDeleted.emit).toHaveBeenCalledWith({ idx: 0 });
    });

    it('should call updateWish service on wish name change', () => {
      const updated_wish = createMockWish(1, 'Updated Name');
      wishService.updateWish.and.returnValue(of(updated_wish));
      spyOn(component.wishUpdated, 'emit');

      component.onChangeDoUpdateWish({ name: updated_wish.name.val });

      expect(wishService.updateWish).toHaveBeenCalledWith(component.wish.id, {
        name: updated_wish.name.val,
      });
    });

    it('should call updateWish service on wish comment change', () => {
      const updated_wish = createMockWish(1, 'Test Wish', 'Updated Comment');
      wishService.updateWish.and.returnValue(of(updated_wish ));
      spyOn(component.wishUpdated, 'emit');

      component.onChangeDoUpdateWish({ comment: updated_wish.name.val });

      expect(wishService.updateWish).toHaveBeenCalledWith(component.wish.id, {
        comment: updated_wish.name.val,
      });
    });

    it('should handle image file selection and update', fakeAsync(() => {
      const mockFile = new File(['test'], 'test.jpg', { type: 'image/jpeg' });
      const mockEvent = {
        target: {
          files: [mockFile]
        }
      } as any;

      const updatedWish = createMockWish();
      wishService.updateWish.and.returnValue(of(updatedWish));
      spyOn(component.wishUpdated, 'emit');

      const mockFileReader = {
        onload: null as any,
        result: null as any,
        readAsArrayBuffer: jasmine.createSpy('readAsArrayBuffer').and.callFake(function(this: any) {
          this.result = new ArrayBuffer(4);
          if (this.onload) this.onload();
        })
      };
      spyOn(window, 'FileReader').and.returnValue(mockFileReader as any);

      component.onFileSelectedDoUpdateImageAndUndoBiggerImage(mockEvent);
      tick();

      expect(wishService.updateWish).toHaveBeenCalled();
      expect(component.is_it_bigger_image_time).toBe(false);
    }));

    it('should toggle bigger image display on mouse events', () => {
      component.onMouseOverImageDoDisplayBiggerImage();
      expect(component.is_it_bigger_image_time).toBe(true);

      component.onMouseOutImageDoUndoBiggerImage();
      expect(component.is_it_bigger_image_time).toBe(false);
    });
  });

  describe('Wish State Management and Updates', () => {
    it('should emit wishUpdated event on successful update', () => {
      const updatedWish = createMockWish(1, 'Updated Name');
      wishService.updateWish.and.returnValue(of(updatedWish));
      spyOn(component.wishUpdated, 'emit');
      spyOn(component, 'createImageSrc');

      component.onChangeDoUpdateWish({ name: updatedWish.name.val});

      expect(component.wishUpdated.emit).toHaveBeenCalledWith({
        wish: updatedWish,
        idx: 0
      });
      expect(component.wish).toBe(updatedWish);
      expect(component.createImageSrc).toHaveBeenCalled();
    });

    it('should revoke old image URL when creating new one', () => {
      spyOn(URL, 'revokeObjectURL');
      spyOn(URL, 'createObjectURL').and.returnValue('blob:new-url');
      (component as any).oldImageSrc = 'blob:old-url';

      component.createImageSrc();

      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:old-url');
      expect((component as any).oldImageSrc).toBe('blob:new-url');
    });

    it('should handle wish with empty picture array', () => {
      component.wish.picture = new Uint8Array();
      spyOn(URL, 'createObjectURL');

      component.createImageSrc();

      expect(URL.createObjectURL).not.toHaveBeenCalled();
      expect(component.imagesrc).toBeNull();
    });
  });

  describe('Wish Validation and Error Handling', () => {
    it('should handle update service error gracefully', () => {
      const error = new Error('Update failed');
      wishService.updateWish.and.returnValue(throwError(() => error));
      spyOn(console, 'error');

      component.onChangeDoUpdateWish({ name: 'Updated Name' });

      expect(console.error).toHaveBeenCalledWith('Error updating wish:', error);
    });

  });
});