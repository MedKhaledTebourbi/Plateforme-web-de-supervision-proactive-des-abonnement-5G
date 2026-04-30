import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { ToastrService } from 'ngx-toastr';

@Injectable()
export class ChantierBlockInterceptor implements HttpInterceptor {
  
  constructor(private toastr: ToastrService) {}
  
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 400 && error.error?.includes('bloqué')) {
          this.toastr.error(error.error, 'Action impossible');
        }
        return throwError(() => error);
      })
    );
  }
}
