import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { catchError, Observable, retry, throwError } from 'rxjs';

@Injectable()
export class ApiInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const modified = req.clone({
      headers: req.headers
        .set('Content-Type', 'application/json')
        .set('Accept', 'application/json'),
    });

    return next.handle(modified).pipe(
      retry({ count: 2, delay: 1000 }),
      catchError((err: HttpErrorResponse) => {
        console.error('[API Error]', err.status, err.message);
        return throwError(() => err);
      })
    );
  }
}