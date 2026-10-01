import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Location } from '@angular/common';
import { fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { AppComponent } from '../app.component';
import { ErrorComponent } from './error.component';
import { appConfig } from '../app.config';

describe('ErrorComponent integration with app.config.ts', () => {
  let router: Router;
  let location: Location;
  let httpMock: HttpTestingController;
  let consoleErrorSpy: jasmine.Spy;

  beforeEach(async () => {
    consoleErrorSpy = spyOn(console, 'error');

    await TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers,
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should navigate to /error on server error', fakeAsync(() => {
    const req = httpMock.expectOne((r) => /wishes\/search/.test(r.url));
    req.flush('fail', { status: 500, statusText: 'Server Error' });

    const fixture = TestBed.createComponent(AppComponent);
    router.initialNavigation();
    
    tick();
    fixture.detectChanges();

    expect(location.path()).toBe('/error');
  }));

  it('should have ErrorComponent instantiated', fakeAsync(() => {
    const req = httpMock.expectOne((r) => /wishes\/search/.test(r.url));
    req.flush('fail', { status: 500, statusText: 'Server Error' });

    const fixture = TestBed.createComponent(AppComponent);
    router.initialNavigation();
    
    tick();
    fixture.detectChanges();

    const errorDe = fixture.debugElement.query(By.directive(ErrorComponent));
    expect(errorDe).toBeTruthy();
  }));

  it('should log something to the console', fakeAsync(() => {
    const req = httpMock.expectOne((r) => /wishes\/search/.test(r.url));
    req.flush('fail', { status: 500, statusText: 'Server Error' });

    TestBed.createComponent(AppComponent);
    router.initialNavigation();
    
    tick();

    expect(consoleErrorSpy).toHaveBeenCalled();
  }));
});
