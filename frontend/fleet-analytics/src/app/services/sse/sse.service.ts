import { inject, Injectable, NgZone } from '@angular/core';
import { Observable, Observer, retry, timeout } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SseService {

  private ngZone = inject(NgZone);

  connect<T = unknown>(url: string): Observable<T> {
    const TIMEOUT_MS = 30000;
    const RECONNECT_DELAY_MS = 3000;

    return new Observable<T>((observer: Observer<T>) => {
      const eventSource = new EventSource(url);

      eventSource.onmessage = (event: MessageEvent) => {
        this.ngZone.run(() => {
          try {
            observer.next(JSON.parse(event.data));
          } catch {
            observer.next(event.data as unknown as T);
          }
        });
      };

      eventSource.onerror = (error) => {
        this.ngZone.run(() => {
          observer.error(error);
        });
      };

      return () => {
        eventSource.close();
      };
    }).pipe(
      timeout({
        each: TIMEOUT_MS,
        with: () => {
          throw new Error(`SSE Timeout: ${url}`);
        }
      }),
      retry({
        delay: RECONNECT_DELAY_MS
      })
    );
  }
}
