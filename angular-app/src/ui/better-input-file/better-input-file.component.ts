import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';


/**
 * remove the  text beside the input but at the same time maintain the tabulation beahavior
 */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-better-input-file',
  imports: [],
  templateUrl: './better-input-file.component.html',
  styleUrl: './better-input-file.component.css'
})
export class BetterInputFileComponent {
  @Input() labelText = "upload file";
  @Output() fileSelected = new EventEmitter<Event>();
  @ViewChild('uploadRef') uploadRef!: ElementRef<HTMLInputElement>;
  
  onEnterDoClickUpload() {
    this.uploadRef.nativeElement.click() ;
  }
  onFileSelectedDoEmit(event: Event) {
    this.fileSelected.emit(event);
  }
}
