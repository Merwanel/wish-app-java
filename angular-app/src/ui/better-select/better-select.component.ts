import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { DisplayMode } from '../../app/wishes/wishes.component';
import { NgClass } from '@angular/common';

export interface option {
  val:string;
  selected_val:string;
  val_to_emit:DisplayMode;
}


/**
 * select whose value in the dropdown can be different than when selected
 */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-better-select',
  imports: [NgClass],
  templateUrl: './better-select.component.html',
  styleUrl: './better-select.component.css'
})
export class BetterSelectComponent {
  @Input({required:true})  options!: option[] ;
  @Input()  label = "" ;
  i_selected = 0 ;
  are_options_displayed = false;
  id_timeout_focusout = 0;

  @Output() select = new EventEmitter<DisplayMode>() ;

  onClickDoToggleDisplayOptions() {
    this.are_options_displayed = !this.are_options_displayed ;
  }
  onClickDoSelectOptionAndCloseDropdownAndEmit(new_i_selected:number) {
    this.i_selected = new_i_selected;
    this.select.emit(this.options[this.i_selected].val_to_emit) ;
    
    this.are_options_displayed = false ;

  }

  

  // Trying to get the behavior of a focusout that triggers
  // only when clicking outside the div and its children

  onFocusInDoCancelFocusOut() {
    window.clearTimeout(this.id_timeout_focusout) ;
  }  
  onFocusOutDoCloseDropdown() {
    this.id_timeout_focusout = window.setTimeout(() => {
      this.are_options_displayed = false ;
    }, 0);
  }
}
