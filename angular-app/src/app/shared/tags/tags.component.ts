import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Tag, TagComponent } from './tag/tag.component';
import { WishService } from '../../wish.service';
import { Wish } from '../../../schemas/wish.schema';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-tags',
  imports: [TagComponent],
  templateUrl: './tags.component.html',
  styleUrl: './tags.component.css'
})
export class TagsComponent {
  @Input({required:true}) wish! : Wish ;
  @Input({required:true}) doUpdateWish!: boolean; 

  constructor(private WishService : WishService) {}

  onChangeDoUpdateWishTags(payload:{index:number, "tag":Tag, "doUpdateWish":boolean}) {
    const are_we_still_in_the_process_of_adding = payload.tag.id === "adding" && payload.tag.val.length === 0
    const cpt_occurences = this.wish.tags.filter((tag) => tag.val === payload.tag.val).length ;
    const if_new_tag_is_it_already_in = payload.tag.id === "adding" && cpt_occurences > 1 ;
    if(are_we_still_in_the_process_of_adding || if_new_tag_is_it_already_in) {
      return ;
    }
    if (payload.tag.val === "") {
      this.wish.tags = this.wish.tags.filter((item, i) => i !== payload.index) ;
    }
    else if(payload.tag.val.length > 0) {
      this.wish.tags[payload.index] = payload.tag ;
    }

    if (this.wish.tags.length > 0 && this.wish.tags[this.wish.tags.length-1].val === "") {
      this.wish.tags.pop() ;
    }
    if (payload.doUpdateWish) {
      this.WishService.updateWish({id:this.wish.id, tags:this.wish.tags.map(({val})=>val)}) ;
    }
  }

  onClickDoAdd() {
    if(this.wish.tags.length === 0 || this.wish.tags[this.wish.tags.length-1].val !== "") {
      this.wish.tags.push({id:"adding",val:'', letters:[]}) ;
    }
  }
}