import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Output, ViewChild, NgZone } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WishService } from '../../wish.service';
import { DynamicInputComponent } from '../../../ui/dynamic-input/dynamic-input.component';
import { TagsComponent } from '../../shared/tags/tags.component';
import { Wish } from '../../../schemas/wish.schema';
import { initLetters } from '../../utils/letters';
import { API_DB_URL } from '../../../const';
import { base64ToUint8Array } from 'uint8array-extras';
import { BetterInputFileComponent } from '../../../ui/better-input-file/better-input-file.component';

interface SearchResult {
  image: string;
}

interface CompletionMessage {
  type: 'complete';
}

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-add-wish',
  imports: [FormsModule, DynamicInputComponent, TagsComponent, BetterInputFileComponent],
  templateUrl: './add-wish.component.html',
  styleUrl: './add-wish.component.css'
})

export class AddWishComponent {
  @Output() close = new EventEmitter();
  @Output() wishAdded = new EventEmitter();
  wish: Wish = {
    id: -1,
    name: { val: '', letters: initLetters('') },
    comment: { val: '', letters: initLetters('') },
    tags: [],
    picture: new Uint8Array(),
    createdAt: ''
  };
  imageUrlDisplayed!: string;
  imageBuffer = new Uint8Array();
  imagesSuggestions: { "buffer": Uint8Array, "display": string }[] = [];
  isSearchingImages = false;

  constructor(private WishService: WishService, private ngZone: NgZone) { }

  @ViewChild('nameInput') nameInput!: ElementRef;
  @ViewChild('commentInput') commentInput!: ElementRef;
  
  onSubmitDoCreateWish() {
    this.WishService.addWish({
      createdAt: new Date().toISOString(),
      name: this.wish.name.val,
      comment: this.wish.comment.val,
      tags: this.wish.tags.map(({ val }) => val),
      picture: this.imageBuffer
    }).subscribe({
      next: (_res) => {
        this.wishAdded.emit();
      },
      error: (err) => {
        throw(err)
      }
    });
  }
  onCancelDoEmitClose() {
    this.close.emit();
  }

  onFileSelectedDoDisplay(event: Event) {
    const input = event.target as HTMLInputElement;

    if (input.files && input.files[0]) {
      const file: File = input.files[0];
      const reader = new FileReader();
      reader.onload = () => {
        const res = this.WishService.convertImage(new Uint8Array(reader.result as ArrayBuffer))
        res.subscribe({
          next: (image) => {
            this.imageBuffer = new Uint8Array(image);
            this.imageUrlDisplayed = URL.createObjectURL(new Blob([this.imageBuffer], { type: 'image/*' }))
          },
          error: (err) => {
            console.error('Error converting image', err);
          }
        });
      };
      reader.readAsArrayBuffer(file);
    }
  }


  onClickDoGetImages(): void {
    const searchTerm: string = this.wish.name.val;
    if (!searchTerm || searchTerm.length < 3) {
      return;
    }

    this.imagesSuggestions = [];
    this.isSearchingImages = true;

    const eventSource: EventSource = new EventSource(`${API_DB_URL}search/${encodeURIComponent(searchTerm)}`);
    
    eventSource.onmessage = (event: MessageEvent): void => {
      this.ngZone.run(() => {
        try {
          const data: SearchResult | CompletionMessage = JSON.parse(event.data);

          if ('type' in data && data.type === 'complete') {
            this.isSearchingImages = false;
            eventSource.close();
          } else if ('image' in data) {
            const image = new Uint8Array(base64ToUint8Array(data.image));
            this.imagesSuggestions.push({ "buffer": image, "display": URL.createObjectURL(new Blob([image], { type: 'image/*' })) });
          }
        } catch (error) {
          console.error('Error parsing SSE data:', error);
          this.isSearchingImages = false;
          eventSource.close();
        }
      });
    };

    eventSource.onerror = (_error: Event): void => {
      this.ngZone.run(() => {
        this.isSearchingImages = false;
      });
      eventSource.close();
    };
  }
  onClickImageSuggestionDoSelect(index: number) {
    this.imageBuffer = this.imagesSuggestions[index].buffer.slice();
    this.imageUrlDisplayed = this.imagesSuggestions[index].display;
  }
}
