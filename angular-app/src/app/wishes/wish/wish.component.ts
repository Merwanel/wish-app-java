import { ChangeDetectionStrategy, Component, ElementRef, Input, ViewChild, Output, EventEmitter, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WishService } from '../../wish.service';
import { WishPartial, WishWRate } from '../../../schemas/wish.schema';
import { TagsComponent } from '../../shared/tags/tags.component';
import { DatePipe } from '@angular/common';
import { DisplayMode } from '../wishes.component';
import { DynamicTextAreaComponent } from '../../../ui/dynamic-text-area/dynamic-text-area.component';



@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-wish',
  imports: [FormsModule, TagsComponent, DynamicTextAreaComponent, DatePipe],
  templateUrl: './wish.component.html',
  styleUrl: './wish.component.css'
})
export class WishComponent implements OnInit {
  @Input({required: true}) display_mode! : DisplayMode ;
  @Input({required : true})  wish!: WishWRate;
  @Input({required : true})  idx!: number;
  imagesrc : string | null = null ;
  is_it_bigger_image_time = false ;
  @Output() wishUpdated = new EventEmitter<{wish:WishWRate, idx: number}>();
  @Output() wishDeleted = new EventEmitter<{idx: number}>();
  
  @ViewChild('nameInput') nameInput!: ElementRef;
  @ViewChild('commentInput') commentInput!: ElementRef;

  constructor(private WishService : WishService) {}
  private oldImageSrc: string | null = null;

  createImageSrc() {
    if (this.oldImageSrc) {
      URL.revokeObjectURL(this.oldImageSrc);
    }
    this.imagesrc = this.wish.picture.length
      ? URL.createObjectURL(new Blob([new Uint8Array(this.wish.picture)], { type: 'image/*' }))
      : null;
    this.oldImageSrc = this.imagesrc;
  }
  ngOnInit() {
    this.createImageSrc() ;
  }

  onChangeDoUpdateWish(wishData: WishPartial) {
    const res = this.WishService.updateWish({ id: this.wish.id, ...wishData });
    res.subscribe({
      next: (wish) => {
        this.wishUpdated.emit({wish, idx:this.idx});
        this.wish = wish;  
        this.createImageSrc();
      },
      error: (err) => {
        console.error('Error updating wish:', err);
      }
    });
  }
  onDeleteWish() {
    this.WishService.deleteWish(this.wish.id) ;
    this.wishDeleted.emit({ idx: this.idx});
  }

  onFileSelectedDoUpdateImageAndUndoBiggerImage(event: Event) {
    const input = event.target as HTMLInputElement;

    if (input.files && input.files[0]) {
      const file: File = input.files[0];
      const reader = new FileReader();
      reader.onload = () => {
        const imageBuffer = new Uint8Array(reader.result as ArrayBuffer);
        this.onChangeDoUpdateWish({id:this.wish.id, picture:imageBuffer}) ;
      };
      reader.readAsArrayBuffer(file);
    }
    this.is_it_bigger_image_time = false ;
  }

  
  onMouseOverImageDoDisplayBiggerImage() {
    this.is_it_bigger_image_time = true ;
  } 
  onMouseOutImageDoUndoBiggerImage() {
    this.is_it_bigger_image_time = false
  }
}
