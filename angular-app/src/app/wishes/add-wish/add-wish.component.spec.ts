import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AddWishComponent } from './add-wish.component';
import { WishService } from '../../wish.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { uint8ArrayToBase64 } from 'uint8array-extras';
import { API_DB_URL } from '../../../const';

describe('AddWishComponent', () => {
  let component: AddWishComponent;
  let fixture: ComponentFixture<AddWishComponent>;

  beforeEach(async () => {
    const wishServiceSpy = jasmine.createSpyObj('WishService', ['addWish', 'convertImage']);

    await TestBed.configureTestingModule({
      imports: [AddWishComponent],
      providers: [
        { provide: WishService, useValue: wishServiceSpy },
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AddWishComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('onClickDoGetImages', () => {
    let mockEventSource: jasmine.SpyObj<EventSource>;

    beforeEach(() => {
      mockEventSource = jasmine.createSpyObj('EventSource', ['close']);
      spyOn(window, 'EventSource').and.returnValue(mockEventSource);
    });

    it('should not create EventSource when search term is too short', () => {
      component.wish.name.val = 'ab';

      component.onClickDoGetImages();

      expect(window.EventSource).not.toHaveBeenCalled();
    });

    it('should not create EventSource when search term is empty', () => {
      component.wish.name.val = '';

      component.onClickDoGetImages();

      expect(window.EventSource).not.toHaveBeenCalled();
    });

    it('should create EventSource with correct URL when search term is valid', () => {
      component.wish.name.val = 'test search';

      component.onClickDoGetImages();

      expect(window.EventSource).toHaveBeenCalledWith(
        `${API_DB_URL}search/test%20search`
      );
    });

    it('should handle search results correctly and add to imagesSuggestions', () => {
      component.wish.name.val = 'test search';
      component.imagesSuggestions = [];

      component.onClickDoGetImages();

      const search_result = new Uint8Array([1,2,3,4])
      const mockEvent = {
        data: JSON.stringify({ image:  uint8ArrayToBase64(search_result) })
      };

      mockEventSource.onmessage!(mockEvent as MessageEvent);

      expect(component.imagesSuggestions.length).toEqual(1);
      expect(component.imagesSuggestions[0].buffer).toEqual(search_result);
    });

    it('should handle completion message correctly', () => {
      component.wish.name.val = 'test';

      component.onClickDoGetImages();

      const mockEvent = {
        data: JSON.stringify({ type: 'complete' })
      };

      mockEventSource.onmessage!(mockEvent as MessageEvent);

      expect(mockEventSource.close).toHaveBeenCalled();
    });

    it('should handle JSON parsing errors gracefully', () => {
      component.wish.name.val = 'test';
      spyOn(console, 'error');

      component.onClickDoGetImages();

      const mockEvent = {
        data: 'invalid json'
      };

      mockEventSource.onmessage!(mockEvent as MessageEvent);

      expect(console.error).toHaveBeenCalledWith('Error parsing SSE data:', jasmine.any(Error));
      expect(mockEventSource.close).toHaveBeenCalled();
    });

    it('should handle URL encoding correctly', () => {
      component.wish.name.val = 'test with spaces & symbols!';

      component.onClickDoGetImages();

      expect(window.EventSource).toHaveBeenCalledWith(
        `${API_DB_URL}search/test%20with%20spaces%20%26%20symbols!`
      );
    });
  });
});